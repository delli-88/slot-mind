package com.slotmind.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.api.PageResponse;
import com.slotmind.api.SlotResponse;
import com.slotmind.api.SlotStatusResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;

import org.springframework.data.domain.Page;

import jakarta.persistence.criteria.Predicate;

@Service
public class SlotService {

	private final SlotRepository slots;

	private final AppointmentRepository appointments;

	public SlotService(SlotRepository slots, AppointmentRepository appointments) {
		this.slots = slots;
		this.appointments = appointments;
	}

	@Transactional(readOnly = true)
	public PageResponse<SlotResponse> list(SlotStatus status, String providerId, Integer page, Integer size) {
		Pageable pageable = Paging.of(page, size, Sort.by("startTime", "id"));
		Specification<Slot> spec = (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (status != null) {
				where.add(cb.equal(root.get("status"), status));
			}
			if (providerId != null && !providerId.isBlank()) {
				where.add(cb.equal(root.get("provider").get("id"), providerId));
			}
			return cb.and(where.toArray(new Predicate[0]));
		};
		Page<Slot> result = slots.findAll(spec, pageable);
		return PageResponse.of(result, result.getContent().stream().map(SlotResponse::from).toList());
	}

	/**
	 * Only CANCELLED can be requested. A CONFIRMED appointment on the slot moves to RESCHEDULE_PENDING in the
	 * same transaction.
	 */
	@Transactional
	public SlotStatusResponse updateStatus(String id, SlotStatus target) {
		Slot slot = slots.findById(id).orElseThrow(() -> new NotFoundException("Slot " + id + " not found"));
		if (target != SlotStatus.CANCELLED) {
			throw new ConflictException("Illegal slot transition", "ILLEGAL_TRANSITION",
					"slot status can only be set to CANCELLED");
		}
		if (slot.getStatus() == SlotStatus.CANCELLED) {
			return new SlotStatusResponse(SlotResponse.from(slot), List.of(), false);
		}
		slot.cancel();
		List<String> affected = new ArrayList<>();
		appointments.findBySlotId(id).filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).ifPresent(a -> {
			a.markReschedulePending();
			affected.add(a.getId());
		});
		slots.saveAndFlush(slot);
		return new SlotStatusResponse(SlotResponse.from(slot), affected, !affected.isEmpty());
	}

}
