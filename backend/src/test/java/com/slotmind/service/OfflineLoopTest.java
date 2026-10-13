package com.slotmind.service;

import static com.slotmind.LoopSupport.setProvider;
import static com.slotmind.LoopSupport.waitForSuggestion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.IntegrationTest;
import com.slotmind.ai.FakeLlmClient;
import com.slotmind.ai.FakeLlmConfig;

/** The full demo through HTTP: Dr. Chen goes offline, the loop plans both patients, the second accept loses. */
@IntegrationTest
@TestPropertySource(properties = { "slotmind.seed.enabled=true", "llm.api.key=test-key" })
@Import(FakeLlmConfig.class)
class OfflineLoopTest {

	private static final String REPLY = "{\"slotId\":\"SLT-005\",\"alternateProviderId\":\"PRV-004\",\"confidence\":0.8,"
			+ "\"reasoning\":\"Dr. Verma is available and has the earliest opening for a general visit.\"}";

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	FakeLlmClient llm;

	@Test
	void offlineReturnsImmediatelyThenBothPatientsGetAnAiSuggestionAndOnlyOneAcceptWins() throws Exception {
		llm.reply(REPLY).reply(REPLY);

		setProvider(mvc, "PRV-001", "OFFLINE").andExpect(status().isOk())
				.andExpect(jsonPath("$.affectedAppointmentIds.length()").value(2))
				.andExpect(jsonPath("$.rescheduleQueued").value(true));

		JsonNode a1 = waitForSuggestion(mvc, json, "APT-001");
		JsonNode a2 = waitForSuggestion(mvc, json, "APT-002");
		for (JsonNode a : new JsonNode[] { a1, a2 }) {
			assertThat(a.path("status").asText()).isEqualTo("RESCHEDULE_PENDING");
			JsonNode s = a.path("pendingSuggestion");
			assertThat(s.path("source").asText()).isEqualTo("LLM");
			assertThat(s.path("triggerReason").asText()).isEqualTo("PROVIDER_OFFLINE");
			assertThat(s.path("recommendedSlot").path("id").asText()).isEqualTo("SLT-005");
			assertThat(s.path("alternateProvider").path("id").asText()).isEqualTo("PRV-004");
		}
		assertThat(llm.calls()).isEqualTo(2);

		String first = a1.path("pendingSuggestion").path("id").asText();
		String second = a2.path("pendingSuggestion").path("id").asText();
		mvc.perform(patch("/suggestions/" + first).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACCEPTED\"}")).andExpect(status().isOk());
		mvc.perform(patch("/suggestions/" + second).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACCEPTED\"}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SLOT_NOT_OPEN")));
	}

}
