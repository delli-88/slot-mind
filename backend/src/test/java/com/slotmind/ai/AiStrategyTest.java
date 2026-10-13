package com.slotmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.scheduling.RuleBasedStrategy;
import com.slotmind.scheduling.SchedulingStrategy.Recommendation;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** A4, A5, A14, A15, A16, A20, A21 with the fake client. */
class AiStrategyTest {

	private final FakeLlmClient llm = new FakeLlmClient();

	private final AiMetrics metrics = new AiMetrics(new SimpleMeterRegistry());

	private final SchedulingRequest request = Requests.offline(AppointmentType.ROUTINE);

	private final String valid = Requests.json("SLT-B", "PRV-B", 0.8, "Dr. Beta can see you later that day.");

	private AiStrategy strategy(String key, long timeoutSeconds) {
		LlmProperties props = new LlmProperties(key, "claude-sonnet-5-5", "low", timeoutSeconds, 2048, 12);
		PromptBuilder prompts = new PromptBuilder("UTC");
		return new AiStrategy(new RuleBasedStrategy(), llm, props, prompts, new LlmResponseValidator(prompts), metrics);
	}

	private AiStrategy strategy() {
		return strategy("test-key", 8);
	}

	private void assertRulesFallback(List<Recommendation> out, FallbackCause cause) {
		Recommendation first = out.get(0);
		assertThat(first.source()).isEqualTo(SuggestionSource.RULES);
		assertThat(first.fallbackCause()).isEqualTo(cause.name());
		assertThat(first.slotId()).isEqualTo("SLT-A"); // the rules' own best choice
		assertThat(metrics.count(cause)).isEqualTo(1.0);
	}

	@Test
	void validReplyIsReturnedAsLlmWithNoFallback() {
		llm.reply(valid);
		List<Recommendation> out = strategy().recommend(request);
		assertThat(out).hasSize(1);
		assertThat(out.get(0).slotId()).isEqualTo("SLT-B");
		assertThat(out.get(0).source()).isEqualTo(SuggestionSource.LLM);
		assertThat(out.get(0).fallbackCause()).isNull();
		assertThat(llm.calls()).isEqualTo(1);
		assertThat(llm.timeouts.get(0)).isLessThanOrEqualTo(Duration.ofSeconds(8));
	}

	@Test
	void a4_malformedThenValidIsAcceptedAfterExactlyTwoCalls() {
		llm.reply("sorry, I cannot do JSON").reply(valid);
		List<Recommendation> out = strategy().recommend(request);
		assertThat(out.get(0).source()).isEqualTo(SuggestionSource.LLM);
		assertThat(llm.calls()).isEqualTo(2);
		assertThat(llm.users.get(1)).contains("Your previous reply was not valid for the schema")
				.contains("Return only the JSON object.");
		assertThat(llm.users.get(0)).doesNotContain("previous reply");
	}

	@Test
	void a5_malformedTwiceFallsBackToRulesWithParseError() {
		llm.reply("nope").reply("still nope");
		List<Recommendation> out = strategy().recommend(request);
		assertRulesFallback(out, FallbackCause.PARSE_ERROR);
		assertThat(llm.calls()).isEqualTo(2);
	}

	@Test
	void repairIsSkippedWhenLessThanThreeSecondsOfBudgetRemain() {
		llm.reply("nope");
		List<Recommendation> out = strategy("test-key", 2).recommend(request);
		assertRulesFallback(out, FallbackCause.PARSE_ERROR);
		assertThat(llm.calls()).isEqualTo(1);
	}

	@Test
	void semanticFailuresAreNotRetried() {
		llm.reply(Requests.json("SLT-999", null, 0.9, "ok"));
		List<Recommendation> out = strategy().recommend(request);
		assertRulesFallback(out, FallbackCause.INVALID_SLOT);
		assertThat(llm.calls()).isEqualTo(1);
	}

	@Test
	void a14_gatewayFailuresMapToTheirCause() {
		for (FallbackCause cause : List.of(FallbackCause.TIMEOUT, FallbackCause.GATEWAY_ERROR, FallbackCause.REFUSED)) {
			FakeLlmClient local = new FakeLlmClient().fail(new LlmException(cause, "x", null));
			AiMetrics m = new AiMetrics(new SimpleMeterRegistry());
			PromptBuilder prompts = new PromptBuilder("UTC");
			AiStrategy s = new AiStrategy(new RuleBasedStrategy(), local,
					new LlmProperties("k", "m", "low", 8, 2048, 12), prompts, new LlmResponseValidator(prompts), m);
			Recommendation first = s.recommend(request).get(0);
			assertThat(first.source()).isEqualTo(SuggestionSource.RULES);
			assertThat(first.fallbackCause()).isEqualTo(cause.name());
			assertThat(m.count(cause)).isEqualTo(1.0);
		}
	}

	@Test
	void a14_anUnexpectedRuntimeFailureIsAGatewayError() {
		llm.fail(new IllegalStateException("boom"));
		assertRulesFallback(strategy().recommend(request), FallbackCause.GATEWAY_ERROR);
	}

	@Test
	void a15_blankKeyNeverCallsTheClient() {
		List<Recommendation> out = strategy("", 8).recommend(request);
		assertRulesFallback(out, FallbackCause.NO_API_KEY);
		assertThat(llm.calls()).isZero();
	}

	@Test
	void a16_lowConfidenceIsStillAnLlmSuggestion() {
		llm.reply(Requests.json("SLT-A", "PRV-A", 0.3, "Not a great fit but possible."));
		Recommendation r = strategy().recommend(request).get(0);
		assertThat(r.source()).isEqualTo(SuggestionSource.LLM);
		assertThat(r.confidence()).isEqualTo(0.3);
		assertThat(metrics.count(FallbackCause.PARSE_ERROR)).isZero();
	}

	@Test
	void a20_noCandidatesMeansNoModelCallAndAnEmptyAnswer() {
		assertThat(strategy().recommend(Requests.noSlots())).isEmpty();
		assertThat(llm.calls()).isZero();
	}

	@Test
	void a21_everyFallbackIsCountedByCause() {
		llm.fail(new LlmException(FallbackCause.TIMEOUT, "t", null)).fail(new LlmException(FallbackCause.TIMEOUT, "t", null))
				.reply(Requests.json(null, null, 0, "none"));
		AiStrategy s = strategy();
		s.recommend(request);
		s.recommend(request);
		s.recommend(request);
		assertThat(metrics.count(FallbackCause.TIMEOUT)).isEqualTo(2.0);
		assertThat(metrics.count(FallbackCause.MODEL_DECLINED)).isEqualTo(1.0);
		assertThat(metrics.count(FallbackCause.INVALID_SLOT)).isZero();
	}

	@Test
	void theCandidateListIsCappedAtTheConfiguredMaximum() {
		LlmProperties props = new LlmProperties("k", "m", "low", 8, 2048, 1);
		PromptBuilder prompts = new PromptBuilder("UTC");
		AiStrategy s = new AiStrategy(new RuleBasedStrategy(), llm, props, prompts, new LlmResponseValidator(prompts),
				metrics);
		llm.reply(Requests.json("SLT-B", "PRV-B", 0.9, "Later.")); // SLT-B was not offered: cap is 1
		assertRulesFallback(s.recommend(request), FallbackCause.INVALID_SLOT);
		assertThat(llm.users.get(0)).contains("SLT-A").doesNotContain("SLT-B");
	}

}
