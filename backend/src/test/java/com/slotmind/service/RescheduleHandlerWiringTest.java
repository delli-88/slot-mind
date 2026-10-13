package com.slotmind.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotmind.IntegrationTest;
import com.slotmind.domain.TriggerReason;

/** The listener fires only on commit: a rolled-back status change must not plan anything. */
@IntegrationTest
class RescheduleHandlerWiringTest {

	@MockitoBean
	RescheduleService reschedule;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	PlatformTransactionManager tx;

	@Test
	void eventIsDroppedOnRollbackAndHandledAfterCommit() {
		TransactionTemplate template = new TransactionTemplate(tx);
		template.executeWithoutResult(status -> {
			events.publishEvent(new RescheduleRequested(TriggerReason.PROVIDER_OFFLINE, List.of("APT-ROLLED-BACK")));
			status.setRollbackOnly();
		});
		template.executeWithoutResult(status -> events
				.publishEvent(new RescheduleRequested(TriggerReason.PROVIDER_OFFLINE, List.of("APT-COMMITTED"))));

		verify(reschedule, timeout(5000)).plan("APT-COMMITTED", TriggerReason.PROVIDER_OFFLINE, null);
		verify(reschedule, never()).plan(eq("APT-ROLLED-BACK"), any(), any());
	}

}
