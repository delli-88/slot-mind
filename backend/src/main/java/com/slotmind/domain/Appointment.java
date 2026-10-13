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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments",
		uniqueConstraints = @UniqueConstraint(name = "uq_appointments_slot", columnNames = "slot_id"),
		indexes = {
				@Index(name = "idx_appointments_provider_status", columnList = "provider_id, status"),
				@Index(name = "idx_appointments_status", columnList = "status") })
public class Appointment {

	@Id
	@Column(length = 20)
	private String id;

	@Column(name = "patient_name", nullable = false, length = 100)
	private String patientName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppointmentType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id", nullable = false)
	private Provider provider;

	/** The current slot only, so UNIQUE here means no two appointments share a slot (I1 backstop). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id", nullable = false)
	private Slot slot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AppointmentStatus status = AppointmentStatus.CONFIRMED;

	@Enumerated(EnumType.STRING)
	@Column(name = "attention_reason", length = 30)
	private AttentionReason attentionReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	private Long version;

	protected Appointment() {
	}

	public Appointment(String id, String patientName, AppointmentType type, Provider provider, Slot slot,
			Instant createdAt) {
		this.id = id;
		this.patientName = patientName;
		this.type = type;
		this.provider = provider;
		this.slot = slot;
		this.createdAt = createdAt;
	}

	/** CONFIRMED -> RESCHEDULE_PENDING. */
	public void markReschedulePending() {
		if (status != AppointmentStatus.CONFIRMED) {
			throw illegal(AppointmentStatus.RESCHEDULE_PENDING);
		}
		status = AppointmentStatus.RESCHEDULE_PENDING;
	}

	/** RESCHEDULE_PENDING -> RESCHEDULED, now pointing at the new slot and its provider. */
	public void reschedule(Slot newSlot) {
		if (status != AppointmentStatus.RESCHEDULE_PENDING) {
			throw illegal(AppointmentStatus.RESCHEDULED);
		}
		this.slot = newSlot;
		this.provider = newSlot.getProvider();
		this.attentionReason = null;
		status = AppointmentStatus.RESCHEDULED;
	}

	/** CONFIRMED | RESCHEDULED -> COMPLETED. */
	public void complete() {
		if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULED) {
			throw illegal(AppointmentStatus.COMPLETED);
		}
		status = AppointmentStatus.COMPLETED;
	}

	public void needsAttention(AttentionReason reason) {
		this.attentionReason = reason;
	}

	public void clearAttention() {
		this.attentionReason = null;
	}

	private IllegalTransitionException illegal(AppointmentStatus target) {
		return new IllegalTransitionException("Appointment " + id + " cannot move " + status + " -> " + target);
	}

	public String getId() {
		return id;
	}

	public String getPatientName() {
		return patientName;
	}

	public AppointmentType getType() {
		return type;
	}

	public Provider getProvider() {
		return provider;
	}

	public Slot getSlot() {
		return slot;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public AttentionReason getAttentionReason() {
		return attentionReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getVersion() {
		return version;
	}

}
