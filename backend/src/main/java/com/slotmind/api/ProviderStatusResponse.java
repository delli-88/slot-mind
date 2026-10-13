package com.slotmind.api;

import java.util.List;

public record ProviderStatusResponse(ProviderResponse provider, List<String> affectedAppointmentIds,
		boolean rescheduleQueued) {
}
