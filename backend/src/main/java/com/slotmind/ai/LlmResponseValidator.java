package com.slotmind.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.scheduling.SchedulingStrategy.ProviderInfo;
import com.slotmind.scheduling.SchedulingStrategy.Recommendation;
import com.slotmind.scheduling.SchedulingStrategy.SchedulingRequest;
import com.slotmind.scheduling.SchedulingStrategy.SlotInfo;

/** The validation pipeline of AI_LAYER 7. Pure: no I/O. Every failure returns a typed cause, never throws. */
@Component
public class LlmResponseValidator {

	private static final Set<String> KEYS = Set.of("slotId", "alternateProviderId", "confidence", "reasoning");

	private static final Pattern ID_LIKE = Pattern.compile("\\b[A-Z]{2,4}-[0-9A-Z]{2,8}\\b");

	private static final int MAX_REASONING = 300;

	private final ObjectMapper json = new ObjectMapper();

	private final PromptBuilder times;

	public LlmResponseValidator(PromptBuilder times) {
		this.times = times;
	}

	/** {@code repairable} marks a failure one "return only the JSON" retry could fix (shape or syntax). */
	public record Outcome(Recommendation recommendation, FallbackCause cause, String detail, boolean repairable) {

		public boolean ok() {
			return recommendation != null;
		}

		static Outcome ok(Recommendation r) {
			return new Outcome(r, null, null, false);
		}

		static Outcome fail(FallbackCause cause, String detail) {
			return new Outcome(null, cause, detail, false);
		}

		static Outcome repair(String detail) {
			return new Outcome(null, FallbackCause.PARSE_ERROR, detail, true);
		}

	}

	public Outcome validate(String raw, SchedulingRequest request, List<Recommendation> candidates) {
		String objectText = extractObject(raw);
		if (objectText == null) {
			return Outcome.repair("no JSON object found");
		}
		JsonNode node;
		try {
			node = json.readTree(objectText);
		}
		catch (JsonProcessingException ex) {
			return Outcome.repair("not valid JSON");
		}
		String shape = shapeError(node);
		if (shape != null) {
			return Outcome.repair(shape);
		}

		if (node.get("slotId").isNull()) {
			return Outcome.fail(FallbackCause.MODEL_DECLINED, "model returned no slot");
		}
		String slotId = node.get("slotId").asText();
		Recommendation chosen = candidates.stream().filter(c -> c.slotId().equals(slotId)).findFirst().orElse(null);
		if (chosen == null) {
			return Outcome.fail(FallbackCause.INVALID_SLOT, "slot is not in the candidate list");
		}
		JsonNode alt = node.get("alternateProviderId");
		if (!alt.isNull() && !alt.asText().equals(chosen.providerId())) {
			return Outcome.fail(FallbackCause.INVALID_PROVIDER, "alternate provider does not own the chosen slot");
		}
		double confidence = node.get("confidence").asDouble();
		if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
			return Outcome.fail(FallbackCause.PARSE_ERROR, "confidence outside 0..1");
		}
		String reasoning = cleanReasoning(node.get("reasoning").asText(), request);
		if (reasoning.isEmpty()) {
			return Outcome.fail(FallbackCause.PARSE_ERROR, "empty reasoning");
		}
		// The stored alternate provider is derived from the slot, never trusted from the model.
		boolean sameProvider = chosen.providerId().equals(request.appointment().providerId());
		return Outcome.ok(new Recommendation(chosen.slotId(), chosen.providerId(),
				sameProvider ? null : chosen.providerId(), confidence, reasoning, SuggestionSource.LLM, null));
	}

	/** All four keys, no others, correct types; a number given as a string is an error. */
	private String shapeError(JsonNode n) {
		if (!n.isObject()) {
			return "not a JSON object";
		}
		for (String k : KEYS) {
			if (!n.has(k)) {
				return "missing key " + k;
			}
		}
		if (n.size() != KEYS.size()) {
			return "unknown key";
		}
		if (!(n.get("slotId").isNull() || n.get("slotId").isTextual())
				|| !(n.get("alternateProviderId").isNull() || n.get("alternateProviderId").isTextual())) {
			return "id fields must be strings or null";
		}
		if (!n.get("confidence").isNumber()) {
			return "confidence must be a number";
		}
		if (!n.get("reasoning").isTextual()) {
			return "reasoning must be a string";
		}
		return null;
	}

	/** Returns the first balanced {...} object (so fences and chatter around it are ignored), or null. */
	static String extractObject(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		int start = raw.indexOf('{');
		if (start < 0) {
			return null;
		}
		int depth = 0;
		boolean inString = false;
		for (int i = start; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (inString) {
				if (c == '\\') {
					i++;
				}
				else if (c == '"') {
					inString = false;
				}
			}
			else if (c == '"') {
				inString = true;
			}
			else if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return raw.substring(start, i + 1);
			}
		}
		return null;
	}

	/** Readable aloud: known ids become names or times, stray ids and markdown go, at most 300 characters. */
	String cleanReasoning(String text, SchedulingRequest request) {
		Map<String, String> names = new HashMap<>();
		for (ProviderInfo p : request.providers()) {
			names.put(p.id(), PromptBuilder.clean(p.name()));
		}
		for (SlotInfo s : request.slots()) {
			names.put(s.id(), times.local(s.startTime()));
		}
		// the appointment's own ids never go to the model, but a model could still echo them
		names.put(request.appointment().id(), "");
		names.put(request.appointment().slotId(), times.local(request.appointment().startTime()));
		String s = text.replaceAll("\\p{Cntrl}", " ");
		// known ids first (exact, longest first so PRV-10 is not clipped by PRV-1), then any other id-looking token
		for (Map.Entry<String, String> e : names.entrySet().stream()
				.sorted((x, y) -> y.getKey().length() - x.getKey().length()).toList()) {
			s = s.replace(e.getKey(), e.getValue());
		}
		Matcher m = ID_LIKE.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(out, Matcher.quoteReplacement(names.getOrDefault(m.group(), "")));
		}
		m.appendTail(out);
		s = out.toString().replaceAll("[*_#`~>\\[\\]|]", "").replaceAll("\\s+", " ").trim();
		if (s.length() > MAX_REASONING) {
			int cut = s.lastIndexOf(' ', MAX_REASONING);
			s = s.substring(0, cut > 0 ? cut : MAX_REASONING).trim();
		}
		return s;
	}

}
