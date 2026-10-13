package com.slotmind.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SlotRepository extends JpaRepository<Slot, String>, JpaSpecificationExecutor<Slot> {

	@Override
	@EntityGraph(attributePaths = "provider")
	Page<Slot> findAll(Specification<Slot> spec, Pageable pageable);

	@EntityGraph(attributePaths = "provider")
	List<Slot> findByStatusAndStartTimeAfter(SlotStatus status, Instant after);

}
