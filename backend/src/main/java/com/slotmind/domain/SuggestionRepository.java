package com.slotmind.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {

	/** One query for a whole page of appointments (no N+1). */
	@EntityGraph(attributePaths = { "appointment", "recommendedSlot", "recommendedSlot.provider",
			"alternateProvider" })
	List<RescheduleSuggestion> findByAppointmentIdInAndStatus(Collection<String> appointmentIds,
			SuggestionStatus status);

	Optional<RescheduleSuggestion> findFirstByAppointmentIdAndStatus(String appointmentId, SuggestionStatus status);

	/** Slots already rejected for this appointment; never suggested again. */
	@Query("select s.recommendedSlot.id from RescheduleSuggestion s "
			+ "where s.appointment.id = :appointmentId and s.status = :status")
	List<String> recommendedSlotIds(@Param("appointmentId") String appointmentId,
			@Param("status") SuggestionStatus status);

}
