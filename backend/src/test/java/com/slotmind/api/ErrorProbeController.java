package com.slotmind.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.domain.IllegalTransitionException;
import com.slotmind.domain.Specialty;
import com.slotmind.service.ConflictException;
import com.slotmind.service.NotFoundException;
import com.slotmind.service.UnprocessableException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Test-only endpoints that raise each error type so the handler can be checked before real endpoints exist. */
@RestController
class ErrorProbeController {

	record Body(@NotBlank String name) {
	}

	@PostMapping("/probe/body")
	void body(@Valid @RequestBody Body body) {
	}

	@GetMapping("/probe/enum")
	void enumParam(@RequestParam Specialty specialty) {
	}

	@GetMapping("/probe/not-found")
	void notFound() {
		throw new NotFoundException("Slot SLT-999 not found");
	}

	@GetMapping("/probe/conflict")
	void conflict() {
		throw new ConflictException("Slot is not available", "SLOT_NOT_OPEN", "SLT-001 is BOOKED");
	}

	@GetMapping("/probe/transition")
	void transition() {
		throw new IllegalTransitionException("Slot SLT-1 cannot move CANCELLED -> OPEN");
	}

	@GetMapping("/probe/unprocessable")
	void unprocessable() {
		throw new UnprocessableException("No suitable slot is available", "NO_CANDIDATE_SLOT");
	}

	@GetMapping("/probe/boom")
	void boom() {
		throw new IllegalStateException("secret internal detail");
	}

}
