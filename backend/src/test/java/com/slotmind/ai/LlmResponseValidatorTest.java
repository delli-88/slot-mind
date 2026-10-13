package com.slotmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.scheduling.RuleBasedStrategy;
import com.slotmind.scheduling.SchedulingStrategy.Recommendation;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;

/** A1-A3, A6-A13: the validation pipeline with no network and no Spring. */
class LlmResponseValidatorTest {

	private final LlmResponseValidator validator = new LlmResponseValidator(new PromptBuilder("UTC"));

	private final SchedulingRequest request = Requests.offline(AppointmentType.ROUTINE);

	private final List<Recommendation> candidates = new RuleBasedStrategy().recommend(request);

	private LlmResponseValidator.Outcome check(String raw) {
		return validator.validate(raw, request, candidates);
	}

	private void assertFails(String raw, FallbackCause cause) {
		LlmResponseValidator.Outcome o = check(raw);
		assertThat(o.ok()).as(raw).isFalse();
		assertThat(o.cause()).as(raw).isEqualTo(cause);
	}

	@Test
	void candidatesAreTheEligibleSlotsOnly() {
		assertThat(candidates).extracting(Recommendation::slotId).containsExactly("SLT-A", "SLT-B");
	}

	@Test
	void a1_validJsonIsMappedAndAlternateIsDerivedFromTheSlot() {
		LlmResponseValidator.Outcome o = check(Requests.json("SLT-A", null, 0.8, "Dr. Alpha can see you sooner."));
		assertThat(o.ok()).isTrue();
		Recommendation r = o.recommendation();
		assertThat(r.slotId()).isEqualTo("SLT-A");
		assertThat(r.alternateProviderId()).isEqualTo("PRV-A"); // model said null, slot belongs to another provider
		assertThat(r.confidence()).isEqualTo(0.8);
		assertThat(r.source()).isEqualTo(SuggestionSource.LLM);
		assertThat(r.fallbackCause()).isNull();
	}

	@Test
	void a2_jsonInsideMarkdownFencesIsAccepted() {
		String fenced = "```json\n" + Requests.json("SLT-B", "PRV-B", 0.6, "Dr. Beta is free.") + "\n```";
		assertThat(check(fenced).ok()).isTrue();
	}

	@Test
	void a3_textAroundTheJsonIsIgnored() {
		String chatty = "Sure! Here you go: " + Requests.json("SLT-A", "PRV-A", 0.7, "Fine.") + " Hope that helps {x}";
		assertThat(check(chatty).ok()).isTrue();
	}

	@Test
	void a6_missingUnknownAndMistypedFieldsAreParseErrors() {
		assertFails("{\"slotId\":\"SLT-A\",\"alternateProviderId\":null,\"confidence\":0.8}", FallbackCause.PARSE_ERROR);
		assertFails("{\"slotId\":\"SLT-A\",\"alternateProviderId\":null,\"confidence\":0.8,\"reasoning\":\"ok\",\"extra\":1}",
				FallbackCause.PARSE_ERROR);
		assertFails(Requests.json("SLT-A", null, "\"0.9\"", "ok"), FallbackCause.PARSE_ERROR);
		assertFails("not json at all", FallbackCause.PARSE_ERROR);
		assertFails("", FallbackCause.PARSE_ERROR);
		assertFails("{\"slotId\": \"SLT-A\",", FallbackCause.PARSE_ERROR);
		assertThat(check("not json at all").repairable()).isTrue();
	}

	@Test
	void a7_unknownSlotIdIsInvalidSlot() {
		assertFails(Requests.json("SLT-999", null, 0.9, "ok"), FallbackCause.INVALID_SLOT);
	}

	@Test
	void a8_slotsThatWereNeverOfferedAreInvalidSlot() {
		for (String id : List.of("SLT-BOOKED", "SLT-OFF", "SLT-PED", "SLT-OLD")) {
			assertFails(Requests.json(id, null, 0.9, "ok"), FallbackCause.INVALID_SLOT);
		}
	}

	@Test
	void a9_alternateProviderThatDoesNotOwnTheSlotIsInvalidProvider() {
		assertFails(Requests.json("SLT-A", "PRV-B", 0.9, "ok"), FallbackCause.INVALID_PROVIDER);
		assertFails(Requests.json("SLT-A", "PRV-NOPE", 0.9, "ok"), FallbackCause.INVALID_PROVIDER);
	}

	@Test
	void a10_confidenceOutOfRangeOrInfiniteIsRejected() {
		assertFails(Requests.json("SLT-A", null, 1.7, "ok"), FallbackCause.PARSE_ERROR);
		assertFails(Requests.json("SLT-A", null, -0.2, "ok"), FallbackCause.PARSE_ERROR);
		assertFails(Requests.json("SLT-A", null, "1e999", "ok"), FallbackCause.PARSE_ERROR);
	}

	@Test
	void a11_blankReasoningIsRejected() {
		assertFails(Requests.json("SLT-A", null, 0.9, "   "), FallbackCause.PARSE_ERROR);
		assertFails(Requests.json("SLT-A", null, 0.9, "***"), FallbackCause.PARSE_ERROR);
	}

	@Test
	void a12_reasoningIsCleanedOfIdsMarkdownAndLength() {
		String messy = "**Take** SLT-A with PRV-A (see APT-X and ZZZ-99) at `once`. " + "word ".repeat(120);
		Recommendation r = check(Requests.json("SLT-A", null, 0.9, messy)).recommendation();
		assertThat(r.reasoning()).doesNotContain("SLT-A", "PRV-A", "APT-X", "ZZZ-99", "*", "`");
		assertThat(r.reasoning()).contains("Dr. Alpha").contains("Tue 13 Oct, 10:40 AM");
		assertThat(r.reasoning().length()).isLessThanOrEqualTo(300);
	}

	@Test
	void a13_nullSlotIdMeansTheModelDeclined() {
		assertFails(Requests.json(null, null, 0, "Nothing suits."), FallbackCause.MODEL_DECLINED);
	}

}
