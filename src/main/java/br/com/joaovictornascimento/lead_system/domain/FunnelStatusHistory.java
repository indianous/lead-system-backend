package br.com.joaovictornascimento.lead_system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Registro imutável de uma transição de etapa do funil — sem @PreUpdate, nunca é alterado. */
@Entity
@Table(name = "funnel_status_histories")
public class FunnelStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lead_id", nullable = false)
	private Lead lead;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 20)
	private FunnelStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false, length = 20)
	private FunnelStatus newStatus;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	private String reason;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	protected FunnelStatusHistory() {
	}

	public FunnelStatusHistory(Lead lead, FunnelStatus previousStatus, FunnelStatus newStatus, User user,
			String reason) {
		this.lead = lead;
		this.previousStatus = previousStatus;
		this.newStatus = newStatus;
		this.user = user;
		this.reason = reason;
	}

	@PrePersist
	void onCreate() {
		this.changedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Lead getLead() {
		return lead;
	}

	public FunnelStatus getPreviousStatus() {
		return previousStatus;
	}

	public FunnelStatus getNewStatus() {
		return newStatus;
	}

	public User getUser() {
		return user;
	}

	public String getReason() {
		return reason;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FunnelStatusHistory other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
