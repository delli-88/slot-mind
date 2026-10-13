package com.slotmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.slotmind.Fixtures;
import com.slotmind.IntegrationTest;
import com.slotmind.domain.Provider;
import com.slotmind.domain.ProviderStatus;
import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotRepository;
import com.slotmind.domain.SlotStatus;
import com.slotmind.domain.Specialty;

@IntegrationTest
class BookAppointmentTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	Fixtures fx;

	@Autowired
	SlotRepository slots;

	private ResultActions book(String json) throws Exception {
		return mvc.perform(post("/appointments").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String body(String patient, String type, String providerId, String slotId) {
		return "{\"patientName\":" + (patient == null ? "null" : "\"" + patient + "\"") + ",\"type\":\"" + type
				+ "\",\"providerId\":\"" + providerId + "\",\"slotId\":\"" + slotId + "\"}";
	}

	@Test
	void booksOpenSlotAndMarksItBooked() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot s = fx.openSlot(p, 60);
		book(body("Maria Lopez", "ROUTINE", p.getId(), s.getId())).andExpect(status().isCreated())
				.andExpect(header().string("Location", startsWith("/appointments/APT-")))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.patientName").value("Maria Lopez"))
				.andExpect(jsonPath("$.provider.id").value(p.getId()))
				.andExpect(jsonPath("$.slot.status").value("BOOKED"))
				.andExpect(jsonPath("$.pendingSuggestion").doesNotExist())
				.andExpect(jsonPath("$.attentionReason").doesNotExist());
		assertThat(slots.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
	}

	@Test
	void secondBookingOfSameSlotIs409() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot s = fx.openSlot(p, 60);
		book(body("A", "ROUTINE", p.getId(), s.getId())).andExpect(status().isCreated());
		book(body("B", "ROUTINE", p.getId(), s.getId())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SLOT_NOT_OPEN")));
	}

	@Test
	void offlineProviderIs409() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.OFFLINE);
		Slot s = fx.openSlot(p, 60);
		book(body("A", "ROUTINE", p.getId(), s.getId())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("PROVIDER_OFFLINE")));
	}

	@Test
	void pastSlotIs409() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot s = fx.openSlot(p, -30);
		book(body("A", "ROUTINE", p.getId(), s.getId())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.details[0]").value(startsWith("SLOT_NOT_OPEN")));
	}

	@Test
	void blankNameAndBadEnumListFieldErrors() throws Exception {
		book("{\"patientName\":\"\",\"type\":\"WALKIN\",\"providerId\":\"P\",\"slotId\":\"S\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("type: must be one of ROUTINE, URGENT, FOLLOW_UP"));
		book(body("", "ROUTINE", "P", "S")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details[0]").value("patientName: must not be blank"));
	}

	@Test
	void missingFieldsAre400() throws Exception {
		book("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.length()").value(4));
	}

	@Test
	void unknownProviderAndSlotAre404() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot s = fx.openSlot(p, 60);
		book(body("A", "ROUTINE", "PRV-999", s.getId())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Provider PRV-999 not found"));
		book(body("A", "ROUTINE", p.getId(), "SLT-999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Slot SLT-999 not found"));
	}

	@Test
	void slotOfAnotherProviderIs400() throws Exception {
		Provider p = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Provider other = fx.provider(Specialty.GENERAL, ProviderStatus.AVAILABLE);
		Slot s = fx.openSlot(other, 60);
		book(body("A", "ROUTINE", p.getId(), s.getId())).andExpect(status().isBadRequest());
	}

}
