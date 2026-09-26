package org.example.service;

import org.example.exception.ChatConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class ChatMemoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    ChatMemoryService memory;

    @BeforeEach void prepareIsolatedDatabase() throws Exception {
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        // Only Testcontainers' disposable database is used here.
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        for (String migration : new String[]{"V1__init.sql", "V2__tool_audit.sql", "V3__action_draft.sql",
                "V7__message_role_provenance.sql", "V13__chat_memory_lifecycle.sql"}) {
            String sql = new ClassPathResource("db/migration/" + migration)
                    .getContentAsString(StandardCharsets.UTF_8).replace("CREATE EXTENSION IF NOT EXISTS vector;", "");
            jdbc.execute(sql);
        }
        memory = new ChatMemoryService(jdbc);
    }

    UUID conversation() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversation(id,user_sub,user_role) VALUES (?,'buyer','BUYER')", id);
        return id;
    }

    void addExchanges(UUID id) {
        jdbc.update("""
                INSERT INTO message(conversation_id,role,content,created_at,required_roles)
                SELECT ?, CASE WHEN i % 2 = 0 THEN 'user' ELSE 'assistant' END, 'message ' || i,
                       now() - interval '1 hour' + i * interval '1 second',
                       CASE WHEN i = 1 THEN 'SELLER' ELSE NULL END
                FROM generate_series(0,39) AS i
                """, id);
    }

    @Test void serializesTurnsAndNeverExecutesAnAcceptedKeyTwice() {
        UUID id = conversation(), request = UUID.randomUUID();
        tx.executeWithoutResult(s -> memory.claim("buyer", id, request, "hello"));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> memory.claim("buyer", id, UUID.randomUUID(), "other")))
                .isInstanceOf(ChatConflictException.class);
        memory.finish(id, request);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> memory.claim("buyer", id, request, "hello")))
                .isInstanceOf(ChatConflictException.class).hasMessageContaining("already accepted");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> memory.claim("other-user", id, UUID.randomUUID(), "hello")))
                .hasMessage("Conversation not found");
    }

    @Test void trimsWholeExchangesAndPreservesRoleRestrictions() {
        UUID id = conversation();
        addExchanges(id);
        tx.executeWithoutResult(s -> memory.retainRecent("buyer", id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE archived_at IS NULL", Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE archived_at IS NOT NULL", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT requirement FROM ai_conversation_access", String.class)).contains("SELLER");
        var cleanup = new ChatHistoryMaintenance(jdbc, memory, true, 30);
        assertThat(tx.<Integer>execute(s -> cleanup.clean())).isZero(); // Grace period, no immediate deletion.
        jdbc.update("UPDATE message SET archived_at = now() - interval '31 days' WHERE archived_at IS NOT NULL");
        assertThat(tx.<Integer>execute(s -> cleanup.clean())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message", Integer.class)).isEqualTo(30);
    }

    @Test void pendingConfirmationsPinTheirHistoryAndCleanupNeverDeletesBusinessReferences() {
        UUID id = conversation();
        addExchanges(id);
        jdbc.update("""
                INSERT INTO action_draft(conversation_id,user_sub,type,payload,idempotency_key,expires_at)
                VALUES (?,'buyer','LEAD','{}',?,now() + interval '30 minutes')
                """, id, UUID.randomUUID().toString());
        tx.executeWithoutResult(s -> memory.retainRecent("buyer", id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE archived_at IS NULL", Integer.class)).isEqualTo(40);
        jdbc.update("UPDATE conversation SET deleted_at = now() - interval '40 days' WHERE id = ?", id);
        var cleanup = new ChatHistoryMaintenance(jdbc, memory, true, 30);
        assertThat(tx.<Integer>execute(s -> cleanup.clean())).isZero();
        jdbc.update("UPDATE action_draft SET status = 'CONFIRMED' WHERE conversation_id = ?", id);
        assertThat(tx.<Integer>execute(s -> cleanup.clean())).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM action_draft", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation WHERE purged_at IS NOT NULL", Integer.class)).isOne();
    }
}
