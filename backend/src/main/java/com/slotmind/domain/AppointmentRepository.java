package com.slotmind.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppointmentRepository extends JpaRepository<Appointment, String>,
		JpaSpecificationExecutor<Appointment> {

	@Override
	@EntityGraph(attributePaths = { "provider", "slot", "slot.provider" })
	Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

	List<Appointment> findByProviderIdAndStatus(String providerId, AppointmentStatus status);

	Optional<Appointment> findBySlotId(String slotId);

}
