package com.slotmind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** Full-context test with MockMvc, its own in-memory DB and no seed data unless a test turns it on. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = { "slotmind.seed.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1" })
@AutoConfigureMockMvc
public @interface IntegrationTest {
}
