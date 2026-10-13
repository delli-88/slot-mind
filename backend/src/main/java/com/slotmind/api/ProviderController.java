package com.slotmind.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.service.ProviderService;

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

}
