package org.example.ai.guest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** A guest token is a capability, never a platform account or a JWT. Only its hash is stored. */
@Service
public class GuestTrialStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int ipDailyLimit;
    private final int globalDailyLimit;

    public GuestTrialStore(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${ai.guest.ip-daily-messages:30}") int ipDailyLimit,
            @Value("${ai.guest.global-daily-messages:1000}") int globalDailyLimit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.ipDailyLimit = Math.max(0, ipDailyLimit);
        this.globalDailyLimit = Math.max(0, globalDailyLimit);
    }

    public record Policy(int messageLimit) {}
    public record Claim(String tokenHash, UUID requestId, String prompt, String status, Map<String, Object> cached) {}

    public Policy policy() {
        return new Policy(jdbc.queryForObject("SELECT message_limit FROM ai_guest_policy WHERE id=1", Integer.class));
    }

    public Policy update(int limit, String actor) {
        if (limit < 0 || limit > 20) throw new IllegalArgumentException("messageLimit must be between 0 and 20");
        jdbc.update("UPDATE ai_guest_policy SET message_limit=?, updated_by=?, updated_at=now() WHERE id=1", limit, actor);
        return policy();
    }

    @Transactional
    public Map<String, Object> create(String address) {
        if (policy().messageLimit() == 0) throw new GuestTrialException("guest_limit_reached", 429);
        consume("create:" + addressHash(address), 20, ChronoUnit.DAYS);
        consume("create:global", 2000, ChronoUnit.DAYS);
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO ai_guest_trial(token_hash) VALUES (?)", hash(token));
        Map<String, Object> result = new LinkedHashMap<>(status(token));
        result.put("token", token);
        return result;
    }

    public Map<String, Object> status(String token) {
        int limit = policy().messageLimit();
        int used = 0;
        List<Map<String, Object>> messages = new ArrayList<>();
        if (token != null && !token.isBlank()) {
            String key = requireToken(token);
            used = jdbc.queryForObject("SELECT used FROM ai_guest_trial WHERE token_hash=?", Integer.class, key);
            List<Map<String, Object>> turns = jdbc.queryForList("""
                    SELECT request_id, prompt, response_json, created_at FROM ai_guest_turn
                    WHERE token_hash=? AND status='DONE' AND response_json IS NOT NULL
                    AND created_at > now() - interval '24 hours' ORDER BY created_at DESC LIMIT 3
                    """, key);
            Collections.reverse(turns);
            for (Map<String, Object> turn : turns) {
                String id = turn.get("request_id").toString();
                String createdAt = ((Timestamp) turn.get("created_at")).toInstant().toString();
                messages.add(Map.of("id", id + "-user", "role", "user", "text", turn.get("prompt"), "createdAt", createdAt));
                Map<String, Object> response = decode((String) turn.get("response_json"));
                messages.add(Map.of("id", id + "-assistant", "role", "assistant", "text", response.get("text"),
                        "resultSets", response.getOrDefault("resultSets", List.of()), "createdAt", createdAt));
            }
        }
        return Map.of("messageLimit", limit, "used", used, "remaining", Math.max(0, limit - used), "messages", messages);
    }

    /** Row locking makes concurrent tabs, devices with the same token and replicas share one counter. */
    @Transactional
    public Claim claim(String token, UUID requestId, String prompt, String address) {
        String key = requireToken(token);
        Integer used = jdbc.queryForObject("SELECT used FROM ai_guest_trial WHERE token_hash=? FOR UPDATE", Integer.class, key);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT content_hash, status, response_json, created_at FROM ai_guest_turn WHERE token_hash=? AND request_id=?", key, requestId);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            if (!hash(prompt).equals(row.get("content_hash"))) throw new IllegalArgumentException("Request ID already used for another message");
            if ("DONE".equals(row.get("status")) && row.get("response_json") != null
                    && ((Timestamp) row.get("created_at")).toInstant().isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) {
                return new Claim(key, requestId, prompt, "DONE", decode((String) row.get("response_json")));
            }
            boolean processing = "PENDING".equals(row.get("status"))
                    && ((Timestamp) row.get("created_at")).toInstant().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES));
            throw new GuestTrialException(processing ? "guest_busy" : "guest_request_recorded", 409);
        }
        if (used >= policy().messageLimit()) throw new GuestTrialException("guest_limit_reached", 429);
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM ai_guest_turn WHERE token_hash=? AND status='PENDING'
                AND created_at > now() - interval '5 minutes'
                """, Integer.class, key);
        if (active != null && active > 0) throw new GuestTrialException("guest_busy", 409);
        consume("msg:" + addressHash(address), ipDailyLimit, ChronoUnit.DAYS);
        consume("msg:global", globalDailyLimit, ChronoUnit.DAYS);
        consume("minute:" + key, 3, ChronoUnit.MINUTES);
        jdbc.update("INSERT INTO ai_guest_turn(token_hash, request_id, content_hash, prompt) VALUES (?,?,?,?)",
                key, requestId, hash(prompt), prompt);
        jdbc.update("UPDATE ai_guest_trial SET used=used+1 WHERE token_hash=?", key);
        return new Claim(key, requestId, prompt, "PENDING", null);
    }

    public List<org.example.ai.provider.ChatMessageInput> context(String tokenHash) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT prompt, response_json FROM ai_guest_turn WHERE token_hash=? AND status='DONE'
                AND response_json IS NOT NULL AND created_at > now()-interval '24 hours'
                ORDER BY created_at DESC LIMIT 3
                """, tokenHash);
        Collections.reverse(rows);
        List<org.example.ai.provider.ChatMessageInput> history = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            history.add(new org.example.ai.provider.ChatMessageInput("user", (String) row.get("prompt")));
            history.add(new org.example.ai.provider.ChatMessageInput("model", (String) decode((String) row.get("response_json")).get("text")));
        }
        return history;
    }

    public void complete(Claim claim, Map<String, Object> answer) {
        try {
            jdbc.update("UPDATE ai_guest_turn SET status='DONE', response_json=? WHERE token_hash=? AND request_id=? AND status='PENDING'",
                    mapper.writeValueAsString(answer), claim.tokenHash(), claim.requestId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public void fail(Claim claim) {
        // Accepted attempts count, including provider failures: retries cannot create unlimited paid calls.
        jdbc.update("UPDATE ai_guest_turn SET status='FAILED' WHERE token_hash=? AND request_id=? AND status='PENDING'", claim.tokenHash(), claim.requestId());
    }

    @Scheduled(fixedDelay = 3_600_000, scheduler = "aiHistoryScheduler")
    public void cleanContent() {
        // Keep tiny quota/idempotency tombstones, never reset allowances through content cleanup.
        jdbc.update("""
                UPDATE ai_guest_turn SET prompt=NULL, response_json=NULL,
                status=CASE WHEN status='PENDING' THEN 'FAILED' ELSE status END
                WHERE (token_hash, request_id) IN (SELECT token_hash, request_id FROM ai_guest_turn
                WHERE created_at < now()-interval '24 hours' AND (prompt IS NOT NULL OR response_json IS NOT NULL) LIMIT 1000)
                """);
        jdbc.update("DELETE FROM ai_guest_rate_window WHERE window_start < now()-interval '2 days'");
    }

    private String requireToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new GuestTrialException("guest_invalid", 401);
        String key = hash(token);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ai_guest_trial WHERE token_hash=?", Integer.class, key);
        if (count == null || count == 0) throw new GuestTrialException("guest_invalid", 401);
        return key;
    }

    private void consume(String scope, int limit, ChronoUnit unit) {
        if (limit <= 0) throw new GuestTrialException("guest_rate_limited", 429);
        int changed = jdbc.update("""
                INSERT INTO ai_guest_rate_window(scope, window_start, used) VALUES(?,?,1)
                ON CONFLICT(scope, window_start) DO UPDATE SET used=ai_guest_rate_window.used+1
                WHERE ai_guest_rate_window.used < ?
                """, scope, Timestamp.from(Instant.now().truncatedTo(unit)), limit);
        if (changed != 1) throw new GuestTrialException("guest_rate_limited", 429);
    }

    private String addressHash(String address) {
        // No raw IP storage and no trust in client-supplied forwarding headers here.
        String salt = jdbc.queryForObject("SELECT ip_salt::text FROM ai_guest_policy WHERE id=1", String.class);
        return hash(salt + ":" + address);
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Map<String, Object> decode(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid guest response record", e); }
    }
}
