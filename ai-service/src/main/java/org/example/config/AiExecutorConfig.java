package org.example.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Map;

/**
 * Chat generation runs on its own executor, never on Tomcat's request threads or (from Phase 5 on)
 * the embedding indexer's pool (PLAN.md §7 item 6). Bean names are deliberately specific — a bean
 * named e.g. {@code taskExecutor} or {@code taskScheduler} would silently override Spring's own
 * defaults, the same class of full-context-boot-only failure as §7 item 4's {@code LocaleResolver}
 * collision.
 */
@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiChatExecutor")
    public ThreadPoolTaskExecutor aiChatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-chat-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setTaskDecorator(task -> {
            Map<String, String> submittingContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> workerContext = MDC.getCopyOfContextMap();
                try {
                    if (submittingContext == null || submittingContext.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(submittingContext);
                    }
                    task.run();
                } finally {
                    if (workerContext == null || workerContext.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(workerContext);
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "aiHeartbeatScheduler")
    public ThreadPoolTaskScheduler aiHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ai-heartbeat-");
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return scheduler;
    }
}
