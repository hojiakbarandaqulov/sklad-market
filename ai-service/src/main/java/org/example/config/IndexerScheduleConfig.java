package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Enables {@code @Scheduled} for the embedding indexer ONLY, on its own dedicated thread pool
 * (PLAN.md §7 item 6: backend services don't use {@code @EnableScheduling}; run indexing on its own
 * executor, never the chat path's). Registering {@code aiIndexerScheduler} as the task scheduler
 * means every scheduled indexer run executes off the chat pools ({@code aiChatExecutor} /
 * {@code aiHeartbeatScheduler}) — so an indexer stall or crash cannot touch chat. The bean name is
 * deliberately specific: a bean named {@code taskScheduler} would silently override Spring's default
 * (the §7 item 4 class of boot-only failure).
 */
@Configuration
@EnableScheduling
public class IndexerScheduleConfig implements SchedulingConfigurer {

    @Bean(name = "aiIndexerScheduler")
    public ThreadPoolTaskScheduler aiIndexerScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ai-indexer-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(aiIndexerScheduler());
    }
}
