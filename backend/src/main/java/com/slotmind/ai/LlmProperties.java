package com.slotmind.ai;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The slotmind.llm.* settings. The API key is deliberately left out of toString. */
@Component
public class LlmProperties {

	private final String apiKey;

	private final String model;

	private final String effort;

	private final Duration timeout;

	private final long maxTokens;

	private final int maxCandidates;

	public LlmProperties(@Value("${llm.api.key:}") String apiKey,
			@Value("${slotmind.llm.model:claude-sonnet-5-5}") String model,
			@Value("${slotmind.llm.effort:low}") String effort,
			@Value("${slotmind.llm.timeout-seconds:8}") long timeoutSeconds,
			@Value("${slotmind.llm.max-tokens:2048}") long maxTokens,
			@Value("${slotmind.llm.max-candidates:12}") int maxCandidates) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
		this.effort = effort;
		this.timeout = Duration.ofSeconds(timeoutSeconds);
		this.maxTokens = maxTokens;
		this.maxCandidates = maxCandidates;
	}

	public boolean hasApiKey() {
		return !apiKey.isEmpty();
	}

	String apiKey() {
		return apiKey;
	}

	public String model() {
		return model;
	}

	public String effort() {
		return effort;
	}

	public Duration timeout() {
		return timeout;
	}

	public long maxTokens() {
		return maxTokens;
	}

	public int maxCandidates() {
		return maxCandidates;
	}

	@Override
	public String toString() {
		return "LlmProperties[model=" + model + ", effort=" + effort + ", timeout=" + timeout + ", apiKeySet="
				+ hasApiKey() + "]";
	}

}
