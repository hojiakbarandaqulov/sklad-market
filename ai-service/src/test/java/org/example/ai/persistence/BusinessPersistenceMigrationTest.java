package org.example.ai.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.example.ai.intent.entity.BuyingIntent;
import org.example.ai.intent.entity.BuyingIntentStatus;
import org.example.ai.intent.repository.BuyingIntentRepository;
import org.example.repository.MessageRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the production Flyway chain against real PostgreSQL + pgvector and then exercises the
 * Spring Data repository against that exact schema. This catches migration-order, vector type,
 * partial-index, JPA mapping and repository-predicate regressions that an in-memory database cannot.
 */
class BusinessPersistenceMigrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private BuyingIntentRepository repository;
    private MessageRepository messageRepository;

    @BeforeAll
    static void migrateProductionSchema() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            dockerAvailable = false;
        }
        assumeTrue(dockerAvailable,
                "Docker is not available - skipping real PostgreSQL/pgvector migration coverage");

        PostgreSQLContainer<?> candidate = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"));
        try {
            candidate.start();
        } catch (RuntimeException unavailable) {
            assumeTrue(false,
                    "PostgreSQL/pgvector Testcontainer could not start: " + unavailable.getMessage());
        }
        postgres = candidate;

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
        entityManagerFactory = createEntityManagerFactory(dataSource);
    }

    @AfterAll
    static void stopContainer() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void openRepository() {
        if (jdbc != null) {
            jdbc.execute("TRUNCATE TABLE buying_intent, message, conversation CASCADE");
        }
        entityManager = entityManagerFactory.createEntityManager();
        repository = new JpaRepositoryFactory(entityManager)
                .getRepository(BuyingIntentRepository.class);
        messageRepository = new JpaRepositoryFactory(entityManager)
                .getRepository(MessageRepository.class);
    }

    @AfterEach
    void closeRepository() {
        if (entityManager != null && entityManager.isOpen()) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    @Test
    void productionFlywayChainRunsV1ThroughV7InOrder() {
        List<String> appliedVersions = jdbc.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success
                ORDER BY installed_rank
                """, String.class);

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void v7AddsBoundedRoleProvenanceForReloadAuthorization() {
        assertThat(columnTypes("message"))
                .containsEntry("required_roles", "character varying");
        assertThat(normalize(indexDefinitions("message")
                .get("idx_message_conversation_required_roles")))
                .contains("conversation_id", "required_roles", "where (required_roles is not null)");
    }

    @Test
    void messageRepositorySeparatesExactV7RolesFromSuccessfulLegacyToolsAndFailedHallucinations() {
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO conversation (id, user_sub, user_role, locale, created_at, updated_at)
                VALUES (?, 'owner-sub', 'BUYER', 'en', ?, ?)
                """, conversationId, Timestamp.from(now), Timestamp.from(now));
        insertToolMessage(conversationId, "search_businesses", "search_businesses completed", null, now);
        insertToolMessage(conversationId, "get_lead", "get_lead completed", null, now.plusMillis(1));
        insertToolMessage(conversationId, "unknown", "unknown failed", null, now.plusMillis(2));
        insertToolMessage(conversationId, "recommend_buyers", "recommend_buyers completed",
                "BUYER,SELLER", now.plusMillis(3));

        assertThat(messageRepository.findDistinctRequiredRolesByConversationId(conversationId))
                .containsExactly("BUYER,SELLER");
        assertThat(messageRepository.findDistinctSuccessfulLegacyToolNamesByConversationId(conversationId))
                .containsExactlyInAnyOrder("search_businesses", "get_lead")
                .doesNotContain("unknown", "recommend_buyers");
    }

    @Test
    void v5CreatesDimensionedCompanyCapabilityIndexAndRunState() {
        Map<String, String> companyColumns = columnTypes("company_embedding");
        assertThat(companyColumns)
                .containsEntry("company_id", "bigint")
                .containsEntry("slug", "character varying")
                .containsEntry("name", "character varying")
                .containsEntry("verification_status", "character varying")
                .containsEntry("category_ids", "ARRAY")
                .containsEntry("region_ids", "ARRAY")
                .containsEntry("product_count", "integer")
                .containsEntry("min_price", "numeric")
                .containsEntry("max_price", "numeric")
                .containsEntry("content_hash", "character varying")
                .containsEntry("indexed_at", "timestamp with time zone");

        String vectorType = jdbc.queryForObject("""
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                WHERE attribute.attrelid = 'company_embedding'::regclass
                  AND attribute.attname = 'embedding'
                  AND NOT attribute.attisdropped
                """, String.class);
        assertThat(vectorType).isEqualTo("vector(768)");

        Map<String, String> indexDefinitions = indexDefinitions("company_embedding");
        assertThat(normalize(indexDefinitions.get("idx_company_embedding_hnsw")))
                .contains("using hnsw", "vector_cosine_ops");
        assertThat(normalize(indexDefinitions.get("idx_company_embedding_categories")))
                .contains("using gin", "category_ids");
        assertThat(normalize(indexDefinitions.get("idx_company_embedding_regions")))
                .contains("using gin", "region_ids");
        assertThat(normalize(indexDefinitions.get("idx_company_embedding_verification")))
                .contains("verification_status");

        assertThat(columnTypes("business_index_state"))
                .containsKeys("id", "last_run_at", "last_status", "companies_indexed", "notes");
        assertThat(normalize(indexDefinitions("business_index_state")
                .get("idx_business_index_state_latest")))
                .contains("last_run_at desc", "id desc");

        // Public contact data must stay source-owned and be hydrated only for shortlisted results.
        assertThat(companyColumns.keySet())
                .noneMatch(name -> name.contains("phone") || name.contains("email")
                        || name.contains("contact") || name.contains("address"));
    }

    @Test
    void v6CreatesPrivateOwnerScopedIntentSchemaConstraintsAndPartialIndex() {
        Map<String, String> columns = columnTypes("buying_intent");
        assertThat(columns)
                .containsEntry("id", "uuid")
                .containsEntry("owner_sub", "character varying")
                .containsEntry("status", "character varying")
                .containsEntry("category", "character varying")
                .containsEntry("region", "character varying")
                .containsEntry("need_text", "character varying")
                .containsEntry("quantity", "numeric")
                .containsEntry("quantity_unit", "character varying")
                .containsEntry("budget_min", "numeric")
                .containsEntry("budget_max", "numeric")
                .containsEntry("currency", "character varying")
                .containsEntry("expires_at", "timestamp with time zone")
                .containsEntry("publication_consent_at", "timestamp with time zone")
                .containsEntry("version", "bigint");
        assertThat(columns.keySet())
                .noneMatch(name -> name.contains("phone") || name.contains("email")
                        || name.contains("contact"));

        Map<String, String> constraints = checkConstraints("buying_intent");
        assertThat(normalize(constraints.get("chk_buying_intent_status")))
                .contains("draft", "published", "closed", "expired");
        assertThat(normalize(constraints.get("chk_buying_intent_quantity")))
                .contains("quantity", ">");
        assertThat(normalize(constraints.get("chk_buying_intent_budget_range")))
                .contains("budget_max", ">=", "budget_min");
        assertThat(normalize(constraints.get("chk_buying_intent_publication_consent")))
                .contains("published", "published_at", "publication_consent_at");

        Map<String, String> indexes = indexDefinitions("buying_intent");
        assertThat(normalize(indexes.get("idx_buying_intent_owner_created")))
                .contains("owner_sub", "created_at desc");
        assertThat(normalize(indexes.get("idx_buying_intent_published_expiry")))
                .contains("expires_at", "category", "region", "where", "published");
        assertThat(normalize(indexes.get("idx_buying_intent_owner_active")))
                .contains("owner_sub", "status", "expires_at", "where", "draft", "published");
        assertThat(normalize(indexes.get("idx_buying_intent_retention")))
                .contains("status", "updated_at", "where", "closed", "expired");
    }

    @Test
    void v6DatabaseConstraintsRejectInvalidIntentStateAndAmounts() {
        Instant future = Instant.now().plusSeconds(3600);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO buying_intent(owner_sub, status, category, need_text, expires_at)
                VALUES (?, 'HIDDEN', ?, ?, ?)
                """, "buyer", "Textiles", "Need cotton", Timestamp.from(future)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO buying_intent(owner_sub, category, need_text, quantity, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, "buyer", "Textiles", "Need cotton", BigDecimal.ZERO, Timestamp.from(future)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO buying_intent(owner_sub, category, need_text, budget_min, budget_max, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "buyer", "Textiles", "Need cotton",
                new BigDecimal("200.00"), new BigDecimal("100.00"), Timestamp.from(future)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO buying_intent(owner_sub, status, category, need_text,
                                          published_at, expires_at)
                VALUES (?, 'PUBLISHED', ?, ?, now(), ?)
                """, "buyer", "Textiles", "Need cotton", Timestamp.from(future)))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                INSERT INTO buying_intent(owner_sub, category, need_text, quantity,
                                          budget_min, budget_max, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "buyer", "Textiles", "Need cotton", new BigDecimal("5.000"),
                new BigDecimal("100.00"), new BigDecimal("200.00"), Timestamp.from(future));
        assertThat(jdbc.queryForObject("SELECT status FROM buying_intent", String.class))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT currency FROM buying_intent", String.class))
                .isEqualTo("UZS");
    }

    @Test
    void repositoryEnforcesOwnerPredicatesAndPessimisticTransitionLookup() throws Exception {
        Instant now = Instant.now();
        BuyingIntent older = persist(intent("owner-a", BuyingIntentStatus.DRAFT,
                "Machinery", "Tashkent", now.minusSeconds(120), now.plusSeconds(3600)));
        BuyingIntent newer = persist(intent("owner-a", BuyingIntentStatus.PUBLISHED,
                "Machinery", "Tashkent", now.minusSeconds(60), now.plusSeconds(3600)));
        persist(intent("owner-b", BuyingIntentStatus.PUBLISHED,
                "Machinery", "Tashkent", now, now.plusSeconds(3600)));

        assertThat(repository.findByIdAndOwnerSub(older.getId(), "owner-a")).isPresent();
        assertThat(repository.findByIdAndOwnerSub(older.getId(), "owner-b")).isEmpty();
        assertThat(repository.findAllByOwnerSubOrderByCreatedAtDesc(
                "owner-a", PageRequest.of(0, 20)).getContent())
                .extracting(BuyingIntent::getId)
                .containsExactly(newer.getId(), older.getId());

        Lock lock = BuyingIntentRepository.class
                .getMethod("findOwnedForUpdate", UUID.class, String.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

        entityManager.getTransaction().begin();
        Optional<BuyingIntent> owned = repository.findOwnedForUpdate(older.getId(), "owner-a");
        Optional<BuyingIntent> foreign = repository.findOwnedForUpdate(older.getId(), "owner-b");
        assertThat(owned).isPresent();
        assertThat(entityManager.getLockMode(owned.orElseThrow()))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(foreign).isEmpty();
        entityManager.getTransaction().commit();
    }

    @Test
    void repositorySearchReturnsOnlyUnexpiredPublishedCaseInsensitiveMatches() {
        Instant now = Instant.now();
        BuyingIntent expectedA = persist(intent("buyer-a", BuyingIntentStatus.PUBLISHED,
                "Electronics", "Tashkent", now.minusSeconds(5), now.plusSeconds(3600)));
        BuyingIntent expectedB = persist(intent("buyer-b", BuyingIntentStatus.PUBLISHED,
                "ELECTRONICS", "TASHKENT", now.minusSeconds(4), now.plusSeconds(7200)));
        persist(intent("draft", BuyingIntentStatus.DRAFT,
                "Electronics", "Tashkent", now.minusSeconds(3), now.plusSeconds(3600)));
        persist(intent("closed", BuyingIntentStatus.CLOSED,
                "Electronics", "Tashkent", now.minusSeconds(2), now.plusSeconds(3600)));
        persist(intent("expired", BuyingIntentStatus.PUBLISHED,
                "Electronics", "Tashkent", now.minusSeconds(1), now.minusSeconds(1)));
        persist(intent("wrong-region", BuyingIntentStatus.PUBLISHED,
                "Electronics", "Samarkand", now, now.plusSeconds(3600)));

        List<BuyingIntent> matches = repository.searchPublished(
                BuyingIntentStatus.PUBLISHED,
                now,
                "electronics",
                "tashkent",
                PageRequest.of(0, 20)).getContent();

        assertThat(matches).extracting(BuyingIntent::getId)
                .containsExactlyInAnyOrder(expectedA.getId(), expectedB.getId());
        assertThat(repository.searchPublished(
                BuyingIntentStatus.PUBLISHED, now, null, null, PageRequest.of(0, 2)).getContent())
                .hasSize(2)
                .allMatch(intent -> intent.getStatus() == BuyingIntentStatus.PUBLISHED
                        && intent.getExpiresAt().isAfter(now));
    }

    private BuyingIntent persist(BuyingIntent intent) {
        entityManager.getTransaction().begin();
        BuyingIntent saved = repository.saveAndFlush(intent);
        entityManager.getTransaction().commit();
        return saved;
    }

    private static void insertToolMessage(
            UUID conversationId, String toolName, String content, String requiredRoles, Instant createdAt) {
        jdbc.update("""
                INSERT INTO message
                    (id, conversation_id, role, content, tool_name, tool_payload, required_roles, created_at)
                VALUES (?, ?, 'TOOL', ?, ?, '{}'::jsonb, ?, ?)
                """, UUID.randomUUID(), conversationId, content, toolName, requiredRoles, Timestamp.from(createdAt));
    }

    private static BuyingIntent intent(String owner, BuyingIntentStatus status,
                                       String category, String region,
                                       Instant createdAt, Instant expiresAt) {
        BuyingIntent intent = new BuyingIntent();
        intent.setOwnerSub(owner);
        intent.setStatus(status);
        intent.setCategory(category);
        intent.setRegion(region);
        intent.setNeedText("Need " + category);
        intent.setCurrency("UZS");
        intent.setCreatedAt(createdAt);
        intent.setUpdatedAt(createdAt);
        intent.setExpiresAt(expiresAt);
        if (status == BuyingIntentStatus.PUBLISHED) {
            intent.setPublishedAt(createdAt);
            intent.setPublicationConsentAt(createdAt);
        } else if (status == BuyingIntentStatus.CLOSED) {
            intent.setClosedAt(createdAt);
        }
        return intent;
    }

    private static EntityManagerFactory createEntityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory =
                new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("org.example.ai.intent.entity", "org.example.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.show_sql", "false"));
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    private static Map<String, String> columnTypes(String table) {
        Map<String, String> columns = new HashMap<>();
        jdbc.queryForList("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, table).forEach(row -> columns.put(
                (String) row.get("column_name"), (String) row.get("data_type")));
        return columns;
    }

    private static Map<String, String> indexDefinitions(String table) {
        Map<String, String> indexes = new HashMap<>();
        jdbc.queryForList("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = ?
                """, table).forEach(row -> indexes.put(
                (String) row.get("indexname"), (String) row.get("indexdef")));
        return indexes;
    }

    private static Map<String, String> checkConstraints(String table) {
        Map<String, String> constraints = new HashMap<>();
        jdbc.queryForList("""
                SELECT checks.constraint_name, checks.check_clause
                FROM information_schema.check_constraints checks
                JOIN information_schema.table_constraints tables
                  ON tables.constraint_schema = checks.constraint_schema
                 AND tables.constraint_name = checks.constraint_name
                WHERE tables.table_schema = current_schema()
                  AND tables.table_name = ?
                """, table).forEach(row -> constraints.put(
                (String) row.get("constraint_name"), (String) row.get("check_clause")));
        return constraints;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
