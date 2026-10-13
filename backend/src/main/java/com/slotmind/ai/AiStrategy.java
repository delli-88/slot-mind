package com.slotmind.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.slotmind.scheduling.RuleBasedStrategy;
import com.slotmind.scheduling.SchedulingStrategy;
import com.slotmind.domain.SuggestionSource;

/**
 * Asks Claude to pick one slot from the closed candidate list that the rules computed. Any failure (no key,
 * timeout, bad JSON, invented slot, ...) returns the rule-based answer instead, with the cause recorded (I7).
 * Runs with no database transaction open (I5).
 */
@Component
public class AiStrategy implements SchedulingStrategy {

	public static final String NAME = "ai";

	private static final Logger log = LoggerFactory.getLogger(AiStrategy.class);

	/** A repair retry only makes sense when this much of the budget is left. */
	private static final Duration MIN_REPAIR_BUDGET = Duration.ofSeconds(3);

	private final RuleBasedStrategy rules;

	private final LlmClient llm;

	private final LlmProperties props;

	private final PromptBuilder prompts;

	private final LlmResponseValidator validator;

	private final AiMetrics metrics;

	public AiStrategy(RuleBasedStrategy rules, LlmClient llm, LlmProperties props, PromptBuilder prompts,
			LlmResponseValidator validator, AiMetrics metrics) {
		this.rules = rules;
		this.llm = llm;
		this.props = props;
		this.prompts = prompts;
		this.validator = validator;
		this.metrics = metrics;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public List<Recommendation> recommend(SchedulingRequest request) {
		List<Recommendation> all = rules.recommend(request);
		if (all.isEmpty()) {
			return all; // nothing to choose from: no model call
		}
		List<Recommendation> candidates = all.subList(0, Math.min(all.size(), props.maxCandidates()));
		if (!props.hasApiKey()) {
			return fallback(all, FallbackCause.NO_API_KEY, request);
		}

		PromptBuilder.Prompt prompt = prompts.build(request, candidates);
		long started = System.nanoTime();
		String user = prompt.user();
		LlmResponseValidator.Outcome outcome = null;
		for (int attempt = 1; attempt <= 2; attempt++) {
			Duration remaining = props.timeout().minusNanos(System.nanoTime() - started);
			String reply;
			try {
				reply = llm.callLLM(prompt.system(), user, remaining);
			}
			catch (LlmException ex) {
				return fallback(all, ex.getFallbackCause(), request);
			}
			catch (RuntimeException ex) {
				log.warn("Unexpected model client failure: {}", ex.getClass().getSimpleName());
				return fallback(all, FallbackCause.GATEWAY_ERROR, request);
			}
			outcome = validator.validate(reply, request, candidates);
			if (outcome.ok()) {
				return new ArrayList<>(List.of(outcome.recommendation()));
			}
			log.debug("AI reply rejected: {} ({})", outcome.cause(), outcome.detail());
			Duration left = props.timeout().minusNanos(System.nanoTime() - started);
			if (!outcome.repairable() || attempt == 2 || left.compareTo(MIN_REPAIR_BUDGET) < 0) {
				break;
			}
			user = prompt.user() + "\nYour previous reply was not valid for the schema: " + outcome.detail()
					+ ". Return only the JSON object.";
		}
		return fallback(all, outcome.cause(), request);
	}

	private List<Recommendation> fallback(List<Recommendation> ranked, FallbackCause cause, SchedulingRequest request) {
		metrics.fallback(cause, request.appointment().id(), request.trigger());
		List<Recommendation> out = new ArrayList<>(ranked);
		Recommendation first = out.get(0);
		out.set(0, new Recommendation(first.slotId(), first.providerId(), first.alternateProviderId(),
				first.confidence(), first.reasoning(), SuggestionSource.RULES, cause.name()));
		return out;
	}

}
