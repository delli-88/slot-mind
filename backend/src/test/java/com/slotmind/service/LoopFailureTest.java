package com.slotmind.service;

import static com.slotmind.LoopSupport.appointment;
import static com.slotmind.LoopSupport.setProvider;
import static com.slotmind.LoopSupport.waitForSuggestion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.ai.FakeLlmClient;
import com.slotmind.ai.FakeLlmConfig;
import com.slotmind.ai.FallbackCause;
import com.slotmind.ai.LlmException;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.TriggerReason;

/** Failure paths and the cancel trigger of the loop (R4, R5, I3). */
@IntegrationTest
@TestPropertySource(properties = "llm.api.key=test-key")
@Import(FakeLlmConfig.class)
@RecordApplicationEvents
class LoopFailureTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	Fixtures fx;

	@Autowired
	FakeLlmClient llm;

	@Autowired
	RescheduleHandler handler;

	@Autowired
	ApplicationEvents events;

	@Test
	void modelTimeoutStillProducesARulesSuggestionWithTheCause() throws Exception {
		Provider chen = fx.provider(Specialty.GENERAL, ProviderStatus.BUSY);
		Provider backup = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment apt = fx.appointment(chen, AppointmentType.ROUTINE, 60);
		Slot target = fx.openSlot(backup, 5); // earliest same-specialty slot in the shared DB
		llm.fail(new LlmException(FallbackCause.TIMEOUT, "slow", null));

		setProvider(mvc, chen.getId(), "OFFLINE");

		JsonNode s = waitForSuggestion(mvc, json, apt.getId()).path("pendingSuggestion");
		assertThat(s.path("source").asText()).isEqualTo("RULES");
		assertThat(s.path("fallbackCause").asText()).isEqualTo("TIMEOUT");
		assertThat(s.path("recommendedSlot").path("id").asText()).isEqualTo(target.getId());
	}

	@Test
	void noCandidateFlagsTheAppointmentForAttentionWithoutCallingTheModel() throws Exception {
		Provider peds = fx.provider(Specialty.PEDIATRIC, ProviderStatus.BUSY);
		Appointment apt = fx.appointment(peds, AppointmentType.ROUTINE, 60);
		int before = llm.calls();

		setProvider(mvc, peds.getId(), "OFFLINE");

		JsonNode a = await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
				.until(() -> appointment(mvc, json, apt.getId()), n -> !n.path("attentionReason").isNull());
		assertThat(a.path("attentionReason").asText()).isEqualTo("NO_CANDIDATE_SLOT");
		assertThat(a.path("pendingSuggestion").isNull()).isTrue();
		assertThat(llm.calls()).isEqualTo(before);
	}

	@Test
	void planningTwiceNeverCreatesADuplicate() throws Exception {
		Provider chen = fx.provider(Specialty.GENERAL, ProviderStatus.BUSY);
		Provider backup = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment apt = fx.appointment(chen, AppointmentType.ROUTINE, 60);
		fx.openSlot(backup, 90);
		llm.fail(new LlmException(FallbackCause.TIMEOUT, "slow", null));
		setProvider(mvc, chen.getId(), "OFFLINE");
		String id = waitForSuggestion(mvc, json, apt.getId()).path("pendingSuggestion").path("id").asText();

		handler.planAll(new RescheduleRequested(TriggerReason.PROVIDER_OFFLINE, List.of(apt.getId())));

		assertThat(appointment(mvc, json, apt.getId()).path("pendingSuggestion").path("id").asText()).isEqualTo(id);
	}

	@Test
	void cancellingABookedSlotPublishesOneEventAndPlansTheCancelledPatient() throws Exception {
		Provider chen = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Provider backup = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment apt = fx.appointment(chen, AppointmentType.ROUTINE, 60);
		fx.openSlot(backup, 90);
		llm.fail(new LlmException(FallbackCause.TIMEOUT, "slow", null));
		long before = events.stream(RescheduleRequested.class).count();

		mvc.perform(patch("/slots/" + apt.getSlot().getId() + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELLED\"}"));

		JsonNode s = waitForSuggestion(mvc, json, apt.getId()).path("pendingSuggestion");
		assertThat(s.path("triggerReason").asText()).isEqualTo("SLOT_CANCELLED");
		assertThat(events.stream(RescheduleRequested.class).count()).isEqualTo(before + 1);
	}

	@Test
	void cancellingAnUnheldSlotPublishesNothing() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot open = fx.openSlot(p, 60);
		long before = events.stream(RescheduleRequested.class).count();

		mvc.perform(patch("/slots/" + open.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELLED\"}"));

		assertThat(events.stream(RescheduleRequested.class).count()).isEqualTo(before);
	}

}
