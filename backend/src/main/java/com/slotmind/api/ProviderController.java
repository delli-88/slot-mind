package com.slotmind.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.service.ProviderService;

import jakarta.validation.Valid;

@RestController
public class ProviderController {

	private final ProviderService providers;

	public ProviderController(ProviderService providers) {
		this.providers = providers;
	}

	@GetMapping("/providers")
	public List<ProviderResponse> list() {
		return providers.list();
	}

	@PatchMapping("/providers/{id}/status")
	public ProviderStatusResponse updateStatus(@PathVariable String id,
			@Valid @RequestBody ProviderStatusRequest request) {
		return providers.updateStatus(id, request.status());
	}

}
