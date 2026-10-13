package com.slotmind.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "slots", indexes = {
		@Index(name = "idx_slots_provider_start", columnList = "provider_id, start_time"),
		@Index(name = "idx_slots_status_start", columnList = "status, start_time") })
public class Slot {

	@Id
	@Column(length = 20)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id", nullable = false)
	private Provider provider;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SlotStatus status = SlotStatus.OPEN;

	@Version
	private Long version;

	protected Slot() {
	}

	public Slot(String id, Provider provider, Instant startTime, int durationMinutes) {
		if (durationMinutes <= 0) {
			throw new IllegalArgumentException("durationMinutes must be > 0");
		}
		this.id = id;
		this.provider = provider;
		this.startTime = startTime;
		this.durationMinutes = durationMinutes;
	}

	/** OPEN -> BOOKED. */
	public void book() {
		require(status == SlotStatus.OPEN, SlotStatus.BOOKED);
		status = SlotStatus.BOOKED;
	}

	/** BOOKED -> OPEN. */
	public void release() {
		require(status == SlotStatus.BOOKED, SlotStatus.OPEN);
		status = SlotStatus.OPEN;
	}

	/** OPEN | BOOKED -> CANCELLED (terminal). */
	public void cancel() {
		require(status == SlotStatus.OPEN || status == SlotStatus.BOOKED, SlotStatus.CANCELLED);
		status = SlotStatus.CANCELLED;
	}

	private void require(boolean allowed, SlotStatus target) {
		if (!allowed) {
			throw new IllegalTransitionException("Slot " + id + " cannot move " + status + " -> " + target);
		}
	}

	public String getId() {
		return id;
	}

	public Provider getProvider() {
		return provider;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public SlotStatus getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

}
