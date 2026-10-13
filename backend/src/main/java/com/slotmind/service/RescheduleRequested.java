package com.slotmind.service;

import java.util.List;

import com.slotmind.domain.TriggerReason;

/** Published inside the transaction that stranded the appointments; acted on only after it commits. */
public record RescheduleRequested(TriggerReason trigger, List<String> appointmentIds) {
}
