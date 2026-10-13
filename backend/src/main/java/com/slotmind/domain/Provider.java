package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "providers", indexes = @Index(name = "idx_providers_status", columnList = "status"))
public class Provider {

	@Id
	@Column(length = 20)
	private String id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Specialty specialty;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProviderStatus status;

	@Version
	private Long version;

	protected Provider() {
	}

	public Provider(String id, String name, Specialty specialty, ProviderStatus status) {
		this.id = id;
		this.name = name;
		this.specialty = specialty;
		this.status = status;
	}

	/** Provider status may move between any two values. */
	public void changeStatus(ProviderStatus target) {
		this.status = target;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Specialty getSpecialty() {
		return specialty;
	}

	public ProviderStatus getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

}
