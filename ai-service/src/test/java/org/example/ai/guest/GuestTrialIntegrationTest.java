package org.example.ai.guest;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class GuestTrialIntegrationTest {
    @Container static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    GuestTrialStore store;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        // Only the disposable container is changed, never a development/server database.
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        jdbc.execute(new ClassPathResource("db/migration/V14__guest_ai_trial.sql").getContentAsString(StandardCharsets.UTF_8));
        store = new GuestTrialStore(jdbc, new ObjectMapper(), 30, 1000);
    }
    String create() { return (String) tx.execute(s -> store.create("192.0.2.1")).get("token"); }
    GuestTrialStore.Claim send(String token, UUID id) { return tx.execute(s -> store.claim(token, id, "hello", "192.0.2.1")); }
    void done(GuestTrialStore.Claim claim) { store.complete(claim, Map.of("text", "Hello!", "resultSets", List.of())); }
    @Test void statusIsFreeAndRetriesDoNotConsumeQuota() {
        assertThat(store.status(null).get("remaining")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_guest_trial", Integer.class)).isZero();
        String token=create(); UUID id=UUID.randomUUID(); done(send(token,id));
        assertThat(send(token,id).cached()).containsEntry("text", "Hello!");
        assertThat(store.status(token).get("used")).isEqualTo(1);
        assertThatThrownBy(() -> tx.execute(s -> store.claim(token,id,"different","192.0.2.1"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dynamicQuotaAndCleanupDoNotResetUsage() {
        store.update(3,"admin"); String token=create();
        for (int i=0;i<3;i++) done(send(token,UUID.randomUUID()));
        assertThatThrownBy(() -> send(token,UUID.randomUUID())).isInstanceOf(GuestTrialException.class).hasMessage("guest_limit_reached");
        jdbc.update("UPDATE ai_guest_turn SET created_at=now()-interval '25 hours'");
        store.cleanContent();
        assertThat(store.status(token).get("messages")).isEqualTo(List.of());
        assertThat(store.status(token).get("remaining")).isEqualTo(0);
        store.update(5,"admin");
        assertThat(store.status(token).get("remaining")).isEqualTo(2);
    }
    @Test void concurrentRequestsCannotSpendTheSameLastCreditTwice() throws Exception {
        store.update(1,"admin"); String token=create();
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> call=() -> { try { send(token,UUID.randomUUID()); return true; } catch(GuestTrialException denied) { return false; } };
            var results=pool.invokeAll(List.of(call,call));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
            assertThat(store.status(token).get("used")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void unknownTokensAndDisabledTrialCannotCreatePaidWork() {
        assertThatThrownBy(() -> send("x".repeat(43),UUID.randomUUID())).hasMessage("guest_invalid");
        store.update(0,"admin");
        assertThatThrownBy(this::create).hasMessage("guest_limit_reached");
    }
}
