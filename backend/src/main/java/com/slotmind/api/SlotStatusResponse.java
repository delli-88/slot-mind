package com.slotmind.api;

import java.util.List;

public record SlotStatusResponse(SlotResponse slot, List<String> affectedAppointmentIds, boolean rescheduleQueued) {
}
