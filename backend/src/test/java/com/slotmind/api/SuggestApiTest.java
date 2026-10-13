package com.slotmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentRepository;
import com.slotmind.domain.AppointmentStatus;
import com.slotmind.domain.AppointmentType;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.Specialty;

@IntegrationTest
class SuggestApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	AppointmentRepository appointments;

	@Test
	void createsAPendingSuggestionAndStrandsAConfirmedAppointment() throws Exception {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot target = fx.openSlot(home, 120);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);

		mvc.perform(post("/appointments/" + a.getId() + "/suggest").param("strategy", "rule"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/appointments/" + a.getId()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.triggerReason").value("INITIAL"))
				.andExpect(jsonPath("$.source").value("RULES"))
				.andExpect(jsonPath("$.recommendedSlot.id").value(target.getId()))
				.andExpect(jsonPath("$.id").value(startsWith("SUG-")));
		assertThat(appointments.findById(a.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
	}

	@Test
	void secondRequestWhileOnePendingIs409() throws Exception {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 120);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		mvc.perform(post("/appointments/" + a.getId() + "/suggest").param("strategy", "rule"))
				.andExpect(status().isCreated());
		mvc.perform(post("/appointments/" + a.getId() + "/suggest").param("strategy", "rule"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("PENDING_SUGGESTION_EXISTS: SUG-")));
	}

	@Test
	void completedAppointmentIs409() throws Exception {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		a.complete();
		appointments.save(a);
		mvc.perform(post("/appointments/" + a.getId() + "/suggest")).andExpect(status().isConflict());
	}

	@Test
	void noCandidateIs422AndAConfirmedAppointmentStaysConfirmed() throws Exception {
		Provider pediatric = fx.provider(Specialty.PEDIATRIC, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(pediatric, AppointmentType.ROUTINE, 60);
		mvc.perform(post("/appointments/" + a.getId() + "/suggest").param("strategy", "rule"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.details[0]").value("NO_CANDIDATE_SLOT"));
		assertThat(appointments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void unknownStrategyIs400AndUnknownAppointmentIs404() throws Exception {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		mvc.perform(post("/appointments/" + a.getId() + "/suggest").param("strategy", "magic"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("strategy: must be one of ai, rule"));
		mvc.perform(post("/appointments/APT-999/suggest")).andExpect(status().isNotFound());
	}

	@Test
	void configuredDefaultStrategyWorksBeforeTheAiStrategyExists() throws Exception {
		Provider home = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		fx.openSlot(fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE), 120);
		Appointment a = fx.appointment(home, AppointmentType.ROUTINE, 60);
		mvc.perform(post("/appointments/" + a.getId() + "/suggest")).andExpect(status().isCreated());
	}

}
