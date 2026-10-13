package com.slotmind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** 3.1: the real gateway must never reach the network without a key, and must never expose it. */
class LlmGatewayTest {

	@Test
	void blankKeyThrowsNoApiKeyWithoutTouchingTheNetwork() {
		LlmProperties props = new LlmProperties("  ", "claude-sonnet-5-5", "low", 8, 2048, 12);
		LLMGateway gateway = new LLMGateway(props);
		assertThat(props.hasApiKey()).isFalse();
		assertThatThrownBy(() -> gateway.callLLM("sys", "user", Duration.ofSeconds(1)))
				.isInstanceOfSatisfying(LlmException.class,
						e -> assertThat(e.getFallbackCause()).isEqualTo(FallbackCause.NO_API_KEY));
	}

	@Test
	void settingsAreAppliedAndTheKeyIsNeverInToString() {
		LlmProperties props = new LlmProperties("sk-secret-123", "claude-sonnet-5-5", "low", 5, 1024, 7);
		assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(props.maxTokens()).isEqualTo(1024);
		assertThat(props.maxCandidates()).isEqualTo(7);
		assertThat(props.model()).isEqualTo("claude-sonnet-5-5");
		assertThat(props.toString()).doesNotContain("sk-secret-123").contains("apiKeySet=true");
	}

	@Test
	void convenienceOverloadDelegatesToTheThreeArgumentCall() {
		FakeLlmClient fake = new FakeLlmClient().reply("hi");
		assertThat(fake.callLLM("just a prompt")).isEqualTo("hi");
		assertThat(fake.users).containsExactly("just a prompt");
		assertThat(fake.timeouts.get(0)).isEqualTo(Duration.ofSeconds(8));
	}

}
