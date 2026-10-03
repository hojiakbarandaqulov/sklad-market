package org.example.ai.guest;

import org.example.ai.business.tool.SearchBusinessesTool;
import org.example.ai.provider.ToolCallRequest;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GuestAnswerServiceTest {
    SearchBusinessesTool search = mock(SearchBusinessesTool.class);
    GuestAnswerService service = new GuestAnswerService(null, search, null, null, null, "test-model");
    @Test void guestsHaveNoAccountToolsEvenIfModelRequestsThem() {
        assertThat(service.executePublicSearch(new ToolCallRequest("a", "get_my_leads", Map.of()), "en", new AtomicBoolean()).success()).isFalse();
        verifyNoInteractions(search);
    }
    @Test void publicSearchIsCappedAndHasNoCallerCredentials() {
        when(search.execute(anyMap(), any())).thenReturn(ToolResult.ok(Map.of("kind", "business_search", "items", List.of())));
        service.executePublicSearch(new ToolCallRequest("a", "search_businesses", Map.of("query", "cement")), "en", new AtomicBoolean());
        verify(search).execute(eq(Map.of("query", "cement", "limit", 3)), argThat((ToolExecutionContext c) -> c.userSub()==null && c.bearerToken()==null && c.roles().isEmpty()));
    }
    @Test void invalidSearchDoesNotInvokeCatalog() {
        assertThat(service.executePublicSearch(new ToolCallRequest("a", "search_businesses", Map.of("query", "")), "en", new AtomicBoolean()).success()).isFalse();
        assertThat(service.executePublicSearch(new ToolCallRequest("a", "search_businesses", Map.of("query", "cement", "limit", 200)), "en", new AtomicBoolean()).success()).isFalse();
        verifyNoInteractions(search);
    }
    @Test void forwardedIpIsTrustedOnlyThroughConfiguredProxyHops() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 192.0.2.10");
        assertThat(new GuestClientAddress("").resolve(request)).isEqualTo("10.0.0.5");
        assertThat(new GuestClientAddress("10.0.0.5").resolve(request)).isEqualTo("192.0.2.10");
    }
}
