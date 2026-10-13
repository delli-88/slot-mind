package com.slotmind.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/** Every fallback is counted by cause (slotmind.ai.fallback) and logged on one line. */
@Component
public class AiMetrics {

	private static final Logger log = LoggerFactory.getLogger(AiMetrics.class);

	private final MeterRegistry registry;

	public AiMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void fallback(FallbackCause cause, String appointmentId, Object trigger) {
		registry.counter("slotmind.ai.fallback", "cause", cause.name()).increment();
		log.warn("ai.fallback cause={} appointment={} trigger={}", cause, appointmentId, trigger);
	}

	public double count(FallbackCause cause) {
		return registry.counter("slotmind.ai.fallback", "cause", cause.name()).count();
	}

}
