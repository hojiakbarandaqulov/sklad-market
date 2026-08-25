package org.example.ai.gateway;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural, permanent regression guard for PLAN.md §4.2 item 1 / Phase 6's "no seller/admin
 * writes" requirement: {@link GatewayClient} — the ONLY HTTP client every tool (seller/admin
 * included) is given — must never grow a POST/PUT/DELETE/PATCH method. As long as this holds, no
 * tool can call a write endpoint even by mistake, regardless of what any individual tool's code
 * does. Mirrors the same invariant Phase 4's {@code ActionDraftConfirmService} javadoc documents
 * for {@code POST /api/v1/leads} (a private {@code RestClient} of its own, never this class).
 */
class GatewayClientHasNoWriteMethodsTest {

    private static final List<String> FORBIDDEN_METHOD_NAME_FRAGMENTS =
            List.of("post", "put", "patch", "delete", "send", "write");

    @Test
    void gatewayClient_exposesOnlyGetMethods() {
        List<Method> publicMethods = Arrays.stream(GatewayClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();

        assertThat(publicMethods).isNotEmpty();
        for (Method method : publicMethods) {
            String lowerName = method.getName().toLowerCase();
            assertThat(FORBIDDEN_METHOD_NAME_FRAGMENTS.stream().anyMatch(lowerName::contains))
                    .as("GatewayClient.%s must not be a write-shaped method", method.getName())
                    .isFalse();
            assertThat(lowerName).as("GatewayClient method names must start with get").startsWith("get");
        }
    }
}
