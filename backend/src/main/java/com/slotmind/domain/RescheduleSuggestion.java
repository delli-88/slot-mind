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
@Table(name = "reschedule_suggestions",
		uniqueConstraints = @UniqueConstraint(name = "uq_suggestions_pending_key", columnNames = "pending_key"),
		indexes = {
				@Index(name = "idx_suggestions_appointment_status", columnList = "appointment_id, status"),
				@Index(name = "idx_suggestions_slot", columnList = "recommended_slot_id") })
public class RescheduleSuggestion {

	@Id
	@Column(length = 20)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "appointment_id", nullable = false)
	private Appointment appointment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recommended_slot_id", nullable = false)
	private Slot recommendedSlot;

	/** Set iff the slot's provider differs from the appointment's provider. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alternate_provider_id")
	private Provider alternateProvider;

	@Column(nullable = false)
	private double confidence;

	@Column(nullable = false, length = 500)
	private String reasoning;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SuggestionSource source;

	@Column(name = "fallback_cause", length = 20)
	private String fallbackCause;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_reason", nullable = false, length = 20)
	private TriggerReason triggerReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SuggestionStatus status = SuggestionStatus.PENDING;

	/** appointmentId:triggerReason while PENDING, NULL once decided (portable one-pending-per-key). */
	@Column(name = "pending_key", length = 80)
	private String pendingKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Version
	private Long version;

	protected RescheduleSuggestion() {
	}

	public RescheduleSuggestion(String id, Appointment appointment, Slot recommendedSlot, Provider alternateProvider,
			double confidence, String reasoning, SuggestionSource source, String fallbackCause,
			TriggerReason triggerReason, Instant createdAt) {
		if (confidence < 0 || confidence > 1) {
			throw new IllegalArgumentException("confidence must be within 0..1");
		}
		this.id = id;
		this.appointment = appointment;
		this.recommendedSlot = recommendedSlot;
		this.alternateProvider = alternateProvider;
		this.confidence = confidence;
		this.reasoning = reasoning;
		this.source = source;
		this.fallbackCause = fallbackCause;
		this.triggerReason = triggerReason;
		this.createdAt = createdAt;
		this.pendingKey = appointment.getId() + ":" + triggerReason;
	}

	/** PENDING -> ACCEPTED. */
	public void accept(Instant now) {
		decide(SuggestionStatus.ACCEPTED, now);
	}

	/** PENDING -> REJECTED. */
	public void reject(Instant now) {
		decide(SuggestionStatus.REJECTED, now);
	}

	private void decide(SuggestionStatus target, Instant now) {
		if (status != SuggestionStatus.PENDING) {
			throw new IllegalTransitionException("Suggestion " + id + " cannot move " + status + " -> " + target);
		}
		status = target;
		decidedAt = now;
		pendingKey = null;
	}

	public String getId() {
		return id;
	}

	public Appointment getAppointment() {
		return appointment;
	}

	public Slot getRecommendedSlot() {
		return recommendedSlot;
	}

	public Provider getAlternateProvider() {
		return alternateProvider;
	}

	public double getConfidence() {
		return confidence;
	}

	public String getReasoning() {
		return reasoning;
	}

	public SuggestionSource getSource() {
		return source;
	}

	public String getFallbackCause() {
		return fallbackCause;
	}

	public TriggerReason getTriggerReason() {
		return triggerReason;
	}

	public SuggestionStatus getStatus() {
		return status;
	}

	public String getPendingKey() {
		return pendingKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public Long getVersion() {
		return version;
	}

}
