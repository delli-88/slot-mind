package com.slotmind.api;

import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Specialty;

public record ProviderResponse(String id, String name, Specialty specialty, ProviderStatus status) {

	public static ProviderResponse from(Provider p) {
		return new ProviderResponse(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
	}

}
