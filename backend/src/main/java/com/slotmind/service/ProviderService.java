package com.slotmind.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotmind.api.ProviderResponse;
import com.slotmind.domain.ProviderRepository;

@Service
public class ProviderService {

	private final ProviderRepository providers;

	public ProviderService(ProviderRepository providers) {
		this.providers = providers;
	}

	@Transactional(readOnly = true)
	public List<ProviderResponse> list() {
		return providers.findAll(Sort.by("name")).stream().map(ProviderResponse::from).toList();
	}

}
