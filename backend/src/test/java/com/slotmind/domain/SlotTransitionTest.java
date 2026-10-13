package com.slotmind.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.slotmind.IntegrationTest;

@IntegrationTest
class SlotTransitionTest {

	@Autowired
	ProviderRepository providers;

	@Autowired
	SlotRepository slots;

	private Slot openSlot() {
		Provider p = new Provider("PRV-T", "Dr Test", Specialty.GENERAL, ProviderStatus.AVAILABLE);
		return new Slot("SLT-T", p, Instant.parse("2030-01-01T10:00:00Z"), 30);
	}

	@Test
	void openToBookedToOpen() {
		Slot s = openSlot();
		assertThat(s.getStatus()).isEqualTo(SlotStatus.OPEN);
		s.book();
		assertThat(s.getStatus()).isEqualTo(SlotStatus.BOOKED);
		s.release();
		assertThat(s.getStatus()).isEqualTo(SlotStatus.OPEN);
	}

	@Test
	void bookedSlotCannotBeBookedAgain() {
		Slot s = openSlot();
		s.book();
		assertThatThrownBy(s::book).isInstanceOf(IllegalTransitionException.class);
	}

	@Test
	void openSlotCannotBeReleased() {
		assertThatThrownBy(() -> openSlot().release()).isInstanceOf(IllegalTransitionException.class);
	}

	@Test
	void cancelledIsTerminal() {
		Slot s = openSlot();
		s.cancel();
		assertThat(s.getStatus()).isEqualTo(SlotStatus.CANCELLED);
		assertThatThrownBy(s::book).isInstanceOf(IllegalTransitionException.class);
		assertThatThrownBy(s::release).isInstanceOf(IllegalTransitionException.class);
		assertThatThrownBy(s::cancel).isInstanceOf(IllegalTransitionException.class);
	}

	@Test
	void bookedSlotCanBeCancelled() {
		Slot s = openSlot();
		s.book();
		s.cancel();
		assertThat(s.getStatus()).isEqualTo(SlotStatus.CANCELLED);
	}

	@Test
	void providerStatusMovesAnyToAny() {
		Provider p = new Provider("PRV-T", "Dr Test", Specialty.GENERAL, ProviderStatus.AVAILABLE);
		p.changeStatus(ProviderStatus.OFFLINE);
		assertThat(p.getStatus()).isEqualTo(ProviderStatus.OFFLINE);
		p.changeStatus(ProviderStatus.BUSY);
		assertThat(p.getStatus()).isEqualTo(ProviderStatus.BUSY);
	}

	@Test
	void persistAndReloadWithVersion() {
		Slot s = openSlot();
		providers.save(s.getProvider());
		slots.save(s);
		Optional<Slot> loaded = slots.findById("SLT-T");
		assertThat(loaded).isPresent();
		assertThat(loaded.get().getProvider().getId()).isEqualTo("PRV-T");
		assertThat(loaded.get().getStartTime()).isEqualTo(Instant.parse("2030-01-01T10:00:00Z"));
		assertThat(loaded.get().getVersion()).isNotNull();
	}

}
