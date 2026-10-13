package com.slotmind.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.slotmind.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "slotmind.seed.enabled=true")
class ProviderApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void listsSeedProvidersSortedByName() throws Exception {
		mvc.perform(get("/providers")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].name").value("Dr. Ananya Iyer"))
				.andExpect(jsonPath("$[4].name").value("Dr. Sarah Chen"))
				.andExpect(jsonPath("$[0].id").value("PRV-005"))
				.andExpect(jsonPath("$[0].specialty").value("SPECIALIST"))
				.andExpect(jsonPath("$[0].status").value("BUSY"))
				.andExpect(jsonPath("$[0].version").doesNotExist());
	}

}
