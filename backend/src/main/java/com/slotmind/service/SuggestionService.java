package com.slotmind.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.api.AppointmentResponse;
import com.slotmind.api.DecisionResponse;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionStatus;

@Service
public class SuggestionService {

	private final SuggestionRepository suggestions;

	private final AppointmentRepository appointments;

	private final Clock clock;

	public SuggestionService(SuggestionRepository suggestions, AppointmentRepository appointments, Clock clock) {
		this.suggestions = suggestions;
		this.appointments = appointments;
		this.clock = clock;
	}

	/**
	 * Accept or reject. Accept is one transaction that re-validates everything (suggestions do not reserve
	 * slots) and then moves slot, appointment and suggestion together. No LLM or other external call here.
	 */
	@Transactional
	public DecisionResponse decide(String id, SuggestionStatus decision) {
		if (decision != SuggestionStatus.ACCEPTED && decision != SuggestionStatus.REJECTED) {
			throw new BadRequestException("Validation failed", List.of("status: must be ACCEPTED or REJECTED"));
		}
		RescheduleSuggestion s = suggestions.findById(id)
				.orElseThrow(() -> new NotFoundException("Suggestion " + id + " not found"));
		if (s.getStatus() != SuggestionStatus.PENDING) {
			throw new ConflictException("Suggestion was already decided", "SUGGESTION_NOT_PENDING",
					id + " is " + s.getStatus());
		}
		Appointment a = s.getAppointment();
		if (decision == SuggestionStatus.REJECTED) {
			s.reject(clock.instant());
			suggestions.saveAndFlush(s);
			return response(s, a);
		}

		if (a.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
			throw new ConflictException("Appointment is not awaiting a new slot", "APPOINTMENT_NOT_PENDING",
					a.getId() + " is " + a.getStatus());
		}
		Slot target = s.getRecommendedSlot();
		if (target.getStatus() != SlotStatus.OPEN) {
			throw new ConflictException("Suggested slot is no longer available", "SLOT_NOT_OPEN",
					target.getId() + " is " + target.getStatus());
		}
		if (target.getProvider().getStatus() == ProviderStatus.OFFLINE) {
			throw new ConflictException("Suggested provider is offline", "PROVIDER_OFFLINE",
					target.getProvider().getId() + " is OFFLINE");
		}
		Slot old = a.getSlot();
		target.book();
		if (old.getStatus() == SlotStatus.BOOKED) {
			old.release();
		}
		a.reschedule(target);
		s.accept(clock.instant());
		// Flush inside the method so a lost race (version / UNIQUE slot_id) surfaces as one 409.
		appointments.saveAndFlush(a);
		suggestions.saveAndFlush(s);
		return response(s, a);
	}

	private DecisionResponse response(RescheduleSuggestion s, Appointment a) {
		return new DecisionResponse(SuggestionResponse.from(s), AppointmentResponse.from(a, null));
	}

}
