package com.slotmind.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.ai.FakeLlmClient;
import com.slotmind.api.SuggestionResponse;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.AttentionReason;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;
import com.slotmind.domain.SuggestionRepository;
import com.slotmind.domain.SuggestionSource;
import com.slotmind.domain.TriggerReason;
import com.slotmind.scheduling.SchedulingStrategy;

/** The AI strategy wired through the real planner, with a scripted model and a real database (A19, A20). */
@IntegrationTest
@TestPropertySource(properties = "llm.api.key=test-key")
@Import(AiRescheduleTest.Config.class)
class AiRescheduleTest {

	/** The scripted model, and a strategy that recommends whatever slot a test points it at (a stale answer). */
	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		FakeLlmClient fakeLlm() {
			return new FakeLlmClient();
		}

		@Bean
		StaleStrategy staleStrategy() {
			return new StaleStrategy();
		}

	}

	static class StaleStrategy implements SchedulingStrategy {

		volatile String slotId;

		volatile String providerId;

		@Override
		public String name() {
			return "stale";
		}

		@Override
		public List<Recommendation> recommend(SchedulingRequest request) {
			return List.of(new Recommendation(slotId, providerId, null, 0.9, "Stale answer.", SuggestionSource.LLM, null));
		}

	}

	@Autowired
	RescheduleService reschedule;

	@Autowired
	Fixtures fx;

	@Autowired
	FakeLlmClient llm;

	@Autowired
	StaleStrategy stale;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	SuggestionRepository suggestions;

	@Test
	void aiAnswerIsPersistedAsLlmWithItsReasoning() {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(home, 150);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		llm.reply("{\"slotId\":\"" + target.getId() + "\",\"alternateProviderId\":null,\"confidence\":0.85,"
				+ "\"reasoning\":\"Your earlier time is no longer available, but " + home.getName() + " can see you later.\"}");
		int before = llm.calls();

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "ai").orElseThrow();

		assertThat(llm.calls() - before).isEqualTo(1);
		assertThat(s.source()).isEqualTo(SuggestionSource.LLM);
		assertThat(s.fallbackCause()).isNull();
		assertThat(s.confidence()).isEqualTo(0.85);
		assertThat(s.recommendedSlot().id()).isEqualTo(target.getId());
		assertThat(s.reasoning()).contains("no longer available");
	}

	@Test
	void badModelOutputStillYieldsASuggestionFromTheRulesWithTheCauseRecorded() {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(home, 150);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		llm.reply("I am not JSON").reply("{broken");

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "ai").orElseThrow();

		assertThat(s.source()).isEqualTo(SuggestionSource.RULES);
		assertThat(s.fallbackCause()).isEqualTo("PARSE_ERROR");
		assertThat(s.recommendedSlot().id()).isEqualTo(target.getId());
		assertThat(suggestions.findById(s.id()).orElseThrow().getFallbackCause()).isEqualTo("PARSE_ERROR");
	}

	@Test
	void ruleStrategyNeverCallsTheModel() {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		fx.openSlot(home, 150);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		int before = llm.calls();

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "rule").orElseThrow();

		assertThat(llm.calls()).isEqualTo(before);
		assertThat(s.source()).isEqualTo(SuggestionSource.RULES);
		assertThat(s.fallbackCause()).isNull();
	}

	@Test
	void a19_slotTakenDuringTheModelCallIsReplannedOnFreshData() {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot taken = fx.openSlot(home, 100);
		Slot free = fx.openSlot(home, 200);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		fx.appointmentOn(home, taken, AppointmentType.ROUTINE); // someone books it "during the model call"
		stale.slotId = taken.getId();
		stale.providerId = home.getId();

		SuggestionResponse s = reschedule.plan(a.getId(), TriggerReason.SLOT_CANCELLED, "stale").orElseThrow();

		assertThat(s.recommendedSlot().id()).isEqualTo(free.getId());
		assertThat(s.source()).isEqualTo(SuggestionSource.RULES);
		assertThat(s.fallbackCause()).isEqualTo("SLOT_NOT_OPEN");
	}

	@Test
	void a19_staleAnswerWithNothingElseLeftFlagsTheAppointment() {
		Provider pediatric = fx.provider(Specialty.PEDIATRIC, ProviderStatus.OFFLINE);
		Provider other = fx.provider(Specialty.PEDIATRIC, ProviderStatus.AVAILABLE);
		Slot only = fx.openSlot(other, 100);
		Appointment a = fx.appointment(pediatric, AppointmentType.ROUTINE, 60);
		a.markReschedulePending();
		appointments.save(a);
		fx.appointmentOn(other, only, AppointmentType.ROUTINE);
		stale.slotId = only.getId();
		stale.providerId = other.getId();

		assertThat(reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "stale")).isEmpty();
		Appointment reloaded = appointments.findById(a.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
		assertThat(reloaded.getAttentionReason()).isEqualTo(AttentionReason.NO_CANDIDATE_SLOT);
	}

	@Test
	void a20_noCandidateSkipsTheModelAndFlagsTheAppointment() {
		Provider pediatric = fx.provider(Specialty.PEDIATRIC, ProviderStatus.OFFLINE);
		Appointment a = fx.appointment(pediatric, AppointmentType.ROUTINE, 60);
		a.markReschedulePending();
		appointments.save(a);
		int before = llm.calls();

		assertThat(reschedule.plan(a.getId(), TriggerReason.PROVIDER_OFFLINE, "ai")).isEmpty();

		assertThat(llm.calls()).isEqualTo(before);
		assertThat(appointments.findById(a.getId()).orElseThrow().getAttentionReason())
				.isEqualTo(AttentionReason.NO_CANDIDATE_SLOT);
	}

}
