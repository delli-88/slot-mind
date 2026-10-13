package com.slotmind.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded pool for the re-planning loop (ADR-005). When the queue is full the task is dropped and logged: the
 * appointments stay RESCHEDULE_PENDING and staff can still use POST /appointments/{id}/suggest.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

	@Bean(name = "rescheduleExecutor")
	ThreadPoolTaskExecutor rescheduleExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(200);
		executor.setThreadNamePrefix("reschedule-");
		executor.setRejectedExecutionHandler((task, pool) -> log
				.error("Reschedule queue is full, dropping a batch; request suggestions manually via POST /suggest"));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}

}
