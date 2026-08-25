package org.example.ai.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.tool.ToolResult;
import org.example.entity.ToolAudit;
import org.example.repository.ToolAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ToolAuditServiceTest {

    @Test
    void failedCallsPersistOnlyFixedRedactionMetadataEvenAfterSchemaValidation() throws Exception {
        ToolAuditRepository repository = mock(ToolAuditRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ToolAuditService service = new ToolAuditService(repository, objectMapper);

        service.record(
                UUID.randomUUID(), UUID.randomUUID(), "buyer-sub", "unknown_tool",
                Map.of("id", 998901234567L, "limit", "+998901234567"),
                ToolResult.error("Not found", 404), 12L, true);

        ArgumentCaptor<ToolAudit> captor = ArgumentCaptor.forClass(ToolAudit.class);
        verify(repository).save(captor.capture());
        Map<?, ?> stored = objectMapper.readValue(captor.getValue().getArguments(), Map.class);
        assertThat(stored.get("redacted")).isEqualTo(true);
        assertThat(stored.get("argumentCount")).isEqualTo(2);
        assertThat(stored.containsKey("id")).isFalse();
        assertThat(stored.containsKey("limit")).isFalse();
    }
}
