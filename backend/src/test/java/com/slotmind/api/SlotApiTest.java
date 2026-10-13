package com.slotmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
class SlotApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	AppointmentRepository appointments;

	private ResultActions set(String id, String json) throws Exception {
		return mvc.perform(patch("/slots/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void filtersByProviderAndStatus() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		fx.openSlot(p, 60);
		fx.openSlot(p, 90);
		fx.appointment(p, AppointmentType.ROUTINE, 120);
		mvc.perform(get("/slots").param("providerId", p.getId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.items[0].providerId").value(p.getId()));
		mvc.perform(get("/slots").param("providerId", p.getId()).param("status", "OPEN"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items[*].status", everyItem(is("OPEN"))));
		mvc.perform(get("/slots").param("providerId", p.getId()).param("size", "2"))
				.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void badListParametersAre400() throws Exception {
		mvc.perform(get("/slots").param("status", "NOPE")).andExpect(status().isBadRequest());
		mvc.perform(get("/slots").param("page", "-1")).andExpect(status().isBadRequest());
	}

	@Test
	void cancellingAHeldSlotStrandsItsAppointment() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Appointment a = fx.appointment(p, AppointmentType.ROUTINE, 60);
		String slotId = a.getSlot().getId();
		set(slotId, "{\"status\":\"CANCELLED\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.slot.status").value("CANCELLED"))
				.andExpect(jsonPath("$.affectedAppointmentIds[0]").value(a.getId()))
				.andExpect(jsonPath("$.rescheduleQueued").value(true));
		assertThat(appointments.findById(a.getId()).orElseThrow().getStatus())
				.isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
	}

	@Test
	void cancellingOpenOrAlreadyCancelledSlotIsANoOpForAppointments() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot open = fx.openSlot(p, 60);
		set(open.getId(), "{\"status\":\"CANCELLED\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.affectedAppointmentIds.length()").value(0))
				.andExpect(jsonPath("$.rescheduleQueued").value(false));
		set(open.getId(), "{\"status\":\"CANCELLED\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.slot.status").value("CANCELLED"))
				.andExpect(jsonPath("$.rescheduleQueued").value(false));
	}

	@Test
	void anyTargetOtherThanCancelledIs409() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot open = fx.openSlot(p, 60);
		set(open.getId(), "{\"status\":\"BOOKED\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("ILLEGAL_TRANSITION")));
	}

	@Test
	void missingStatusIs400AndUnknownSlotIs404() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot open = fx.openSlot(p, 60);
		set(open.getId(), "{}").andExpect(status().isBadRequest());
		set("SLT-999", "{\"status\":\"CANCELLED\"}").andExpect(status().isNotFound());
	}

}
