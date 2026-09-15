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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "lead_type", nullable = false, length = 20)
	private LeadType leadType;

	private String phone;

	private String email;

	@Column(name = "initial_message")
	private String initialMessage;

	@Column(name = "estimated_budget_cents")
	private Integer estimatedBudgetCents;

	@Column(name = "desired_timeline")
	private String desiredTimeline;

	@Enumerated(EnumType.STRING)
	@Column(name = "qualification_score", length = 10)
	private QualificationScore qualificationScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "funnel_status", nullable = false, length = 20)
	private FunnelStatus funnelStatus;

	@Column(name = "loss_reason")
	private String lossReason;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "origin_id", nullable = false)
	private LeadOrigin origin;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "assigned_user_id", nullable = false)
	private User assignedUser;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "lead_products", joinColumns = @JoinColumn(name = "lead_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
	private Set<Product> productsOfInterest = new HashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Lead() {
	}

	public Lead(String name, LeadType leadType, String phone, String email, String initialMessage,
			Integer estimatedBudgetCents, String desiredTimeline, QualificationScore qualificationScore,
			LeadOrigin origin, User assignedUser, Set<Product> productsOfInterest) {
		this.name = name;
		this.leadType = leadType;
		this.phone = phone;
		this.email = email;
		this.initialMessage = initialMessage;
		this.estimatedBudgetCents = estimatedBudgetCents;
		this.desiredTimeline = desiredTimeline;
		this.qualificationScore = qualificationScore;
		this.funnelStatus = FunnelStatus.NEW;
		this.origin = origin;
		this.assignedUser = assignedUser;
		this.productsOfInterest = productsOfInterest != null ? productsOfInterest : new HashSet<>();
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LeadType getLeadType() {
		return leadType;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getInitialMessage() {
		return initialMessage;
	}

	public void setInitialMessage(String initialMessage) {
		this.initialMessage = initialMessage;
	}

	public Integer getEstimatedBudgetCents() {
		return estimatedBudgetCents;
	}

	public void setEstimatedBudgetCents(Integer estimatedBudgetCents) {
		this.estimatedBudgetCents = estimatedBudgetCents;
	}

	public String getDesiredTimeline() {
		return desiredTimeline;
	}

	public void setDesiredTimeline(String desiredTimeline) {
		this.desiredTimeline = desiredTimeline;
	}

	public QualificationScore getQualificationScore() {
		return qualificationScore;
	}

	public void setQualificationScore(QualificationScore qualificationScore) {
		this.qualificationScore = qualificationScore;
	}

	public FunnelStatus getFunnelStatus() {
		return funnelStatus;
	}

	public String getLossReason() {
		return lossReason;
	}

	public LeadOrigin getOrigin() {
		return origin;
	}

	public User getAssignedUser() {
		return assignedUser;
	}

	public void setAssignedUser(User assignedUser) {
		this.assignedUser = assignedUser;
	}

	public Set<Product> getProductsOfInterest() {
		return productsOfInterest;
	}

	public void setProductsOfInterest(Set<Product> productsOfInterest) {
		this.productsOfInterest = productsOfInterest;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Lead other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
