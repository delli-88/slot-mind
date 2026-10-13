package com.slotmind.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.domain.SlotStatus;
import com.slotmind.service.SlotService;

import jakarta.validation.Valid;

@RestController
public class SlotController {

	private final SlotService slots;

	public SlotController(SlotService slots) {
		this.slots = slots;
	}

	@GetMapping("/slots")
	public PageResponse<SlotResponse> list(@RequestParam(required = false) SlotStatus status,
			@RequestParam(required = false) String providerId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return slots.list(status, providerId, page, size);
	}

	@PatchMapping("/slots/{id}/status")
	public SlotStatusResponse updateStatus(@PathVariable String id, @Valid @RequestBody SlotStatusRequest request) {
		return slots.updateStatus(id, request.status());
	}

}
