package com.slotmind.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.slotmind.domain.TriggerReason;

class RescheduleHandlerTest {

	@Test
	void plansEveryStrandedAppointmentWithTheEventsTrigger() {
		RescheduleService reschedule = mock(RescheduleService.class);
		new RescheduleHandler(reschedule)
				.planAll(new RescheduleRequested(TriggerReason.SLOT_CANCELLED, List.of("APT-1", "APT-2")));
		verify(reschedule).plan("APT-1", TriggerReason.SLOT_CANCELLED, null);
		verify(reschedule).plan("APT-2", TriggerReason.SLOT_CANCELLED, null);
	}

	@Test
	void oneFailingAppointmentNeverStopsTheOthers() {
		RescheduleService reschedule = mock(RescheduleService.class);
		when(reschedule.plan(eq("APT-1"), any(), any())).thenThrow(new IllegalStateException("boom"));
		when(reschedule.plan(eq("APT-2"), any(), any()))
				.thenThrow(new ConflictException("Conflict", "PENDING_SUGGESTION_EXISTS", "dup"));
		new RescheduleHandler(reschedule)
				.planAll(new RescheduleRequested(TriggerReason.PROVIDER_OFFLINE, List.of("APT-1", "APT-2", "APT-3")));
		verify(reschedule).plan("APT-3", TriggerReason.PROVIDER_OFFLINE, null);
	}

}
