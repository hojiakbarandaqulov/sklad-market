package org.example.config;

import org.example.ai.observability.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class AiExecutorConfigTest {

    @Test
    void chatExecutorPropagatesRequestIdAndClearsItBetweenTasks() throws Exception {
        ThreadPoolTaskExecutor executor = new AiExecutorConfig().aiChatExecutor();
        try {
            MDC.put(RequestIdFilter.MDC_KEY, "request-123");
            Future<String> propagated = executor.submit(() -> MDC.get(RequestIdFilter.MDC_KEY));
            assertThat(propagated.get()).isEqualTo("request-123");
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("request-123");

            MDC.clear();
            Future<String> cleared = executor.submit(() -> MDC.get(RequestIdFilter.MDC_KEY));
            assertThat(cleared.get()).isNull();
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
