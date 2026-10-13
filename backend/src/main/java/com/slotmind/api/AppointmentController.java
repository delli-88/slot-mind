package com.slotmind.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.domain.AppointmentStatus;
import com.slotmind.service.AppointmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

	private final AppointmentService appointments;

	public AppointmentController(AppointmentService appointments) {
		this.appointments = appointments;
	}

	@PostMapping
	public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody AppointmentRequest request) {
		AppointmentResponse created = appointments.book(request);
		return ResponseEntity.created(URI.create("/appointments/" + created.id())).body(created);
	}

	@GetMapping
	public PageResponse<AppointmentResponse> list(@RequestParam(required = false) AppointmentStatus status,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return appointments.list(status, page, size);
	}

	@GetMapping("/{id}")
	public AppointmentResponse get(@PathVariable String id) {
		return appointments.get(id);
	}

	@PostMapping("/{id}/suggest")
	public ResponseEntity<SuggestionResponse> suggest(@PathVariable String id,
			@RequestParam(required = false) String strategy) {
		SuggestionResponse created = appointments.suggest(id, strategy);
		return ResponseEntity.created(URI.create("/appointments/" + id)).body(created);
	}

}
