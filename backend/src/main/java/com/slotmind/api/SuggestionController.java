package com.slotmind.api;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.slotmind.service.SuggestionService;

import jakarta.validation.Valid;

@RestController
public class SuggestionController {

	private final SuggestionService suggestions;

	public SuggestionController(SuggestionService suggestions) {
		this.suggestions = suggestions;
	}

	@PatchMapping("/suggestions/{id}")
	public DecisionResponse decide(@PathVariable String id, @Valid @RequestBody DecisionRequest request) {
		return suggestions.decide(id, request.status());
	}

}
