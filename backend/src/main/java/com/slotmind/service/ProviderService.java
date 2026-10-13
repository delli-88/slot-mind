package com.slotmind.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.api.ProviderResponse;
import com.slotmind.api.ProviderStatusResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderRepository;
import com.slotmind.domain.ProviderStatus;

@Service
public class ProviderService {

	private final ProviderRepository providers;

	private final AppointmentRepository appointments;

	public ProviderService(ProviderRepository providers, AppointmentRepository appointments) {
		this.providers = providers;
		this.appointments = appointments;
	}

	@Transactional(readOnly = true)
	public List<ProviderResponse> list() {
		return providers.findAll(Sort.by("name")).stream().map(ProviderResponse::from).toList();
	}

	/**
	 * Changes the status. Going OFFLINE moves the provider's CONFIRMED appointments to RESCHEDULE_PENDING in the
	 * same transaction.
	 */
	@Transactional
	public ProviderStatusResponse updateStatus(String id, ProviderStatus target) {
		Provider provider = providers.findById(id)
				.orElseThrow(() -> new NotFoundException("Provider " + id + " not found"));
		if (provider.getStatus() == target) {
			return new ProviderStatusResponse(ProviderResponse.from(provider), List.of(), false);
		}
		provider.changeStatus(target);
		List<String> affected = List.of();
		if (target == ProviderStatus.OFFLINE) {
			List<Appointment> stranded = appointments.findByProviderIdAndStatus(id, AppointmentStatus.CONFIRMED);
			stranded.forEach(Appointment::markReschedulePending);
			affected = stranded.stream().map(Appointment::getId).sorted().toList();
		}
		providers.saveAndFlush(provider);
		return new ProviderStatusResponse(ProviderResponse.from(provider), affected, !affected.isEmpty());
	}

}
