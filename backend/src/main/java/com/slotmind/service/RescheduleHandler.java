package com.slotmind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The agentic loop (ADR-005): after the status change has committed, plan a suggestion for every stranded
 * appointment on the bounded executor. The HTTP call that caused it has already returned.
 */
@Component
public class RescheduleHandler {

	private static final Logger log = LoggerFactory.getLogger(RescheduleHandler.class);

	private final RescheduleService reschedule;

	public RescheduleHandler(RescheduleService reschedule) {
		this.reschedule = reschedule;
	}

	/** Runs only if the publishing transaction committed; a rollback silently drops the event. */
	@Async("rescheduleExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(RescheduleRequested event) {
		planAll(event);
	}

	/** Synchronous core, one appointment at a time; one failing id never stops the others. */
	public void planAll(RescheduleRequested event) {
		for (String id : event.appointmentIds()) {
			try {
				boolean planned = reschedule.plan(id, event.trigger(), null).isPresent();
				log.info("loop trigger={} appointment={} suggestion={}", event.trigger(), id,
						planned ? "created" : "none (needs attention)");
			}
			catch (ConflictException ex) {
				log.info("loop trigger={} appointment={} skipped: {}", event.trigger(), id, ex.getDetail());
			}
			catch (RuntimeException ex) {
				log.error("loop trigger={} appointment={} failed", event.trigger(), id, ex);
			}
		}
	}

}
