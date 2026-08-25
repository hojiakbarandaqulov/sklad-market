package org.example.ai.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.tool.ToolResult;
import org.example.entity.ToolAudit;
import org.example.repository.ToolAuditRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Persists one {@code tool_audit} row per tool execution (PLAN.md Phase 2: "Every tool execution
 * audited"). Stores the argument summary, not full conversation text (§4.2 item 8: least logging).
 */
@Slf4j
@Service
public class ToolAuditService {

    private final ToolAuditRepository repository;
    private final ObjectMapper objectMapper;

    public ToolAuditService(ToolAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(
            UUID conversationId,
            UUID messageId,
            String userSub,
            String toolName,
            Map<String, Object> arguments,
            ToolResult result,
            long latencyMillis,
            boolean argumentsValidated) {
        ToolAudit audit = new ToolAudit();
        audit.setConversationId(conversationId);
        audit.setMessageId(messageId);
        audit.setUserSub(userSub);
        audit.setToolName(toolName);
        audit.setArguments(writeJson(argumentsValidated && result.success()
                ? ToolAuditSanitizer.sanitize(toolName, arguments)
                : ToolAuditSanitizer.sanitizeRejected(toolName, arguments)));
        audit.setResultStatus(result.success() ? "ok" : "error");
        audit.setHttpStatus(result.httpStatus());
        audit.setLatencyMs((int) latencyMillis);
        repository.save(audit);
    }

    private String writeJson(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool arguments for audit, storing empty object", e);
            return "{}";
        }
    }
}
