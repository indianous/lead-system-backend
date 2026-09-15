package br.com.joaovictornascimento.lead_system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductType type;

	private String description;

	@Column(name = "min_price_cents")
	private Integer minPriceCents;

	@Column(name = "max_price_cents")
	private Integer maxPriceCents;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Product() {
	}

	public Product(String name, ProductType type, String description, Integer minPriceCents, Integer maxPriceCents) {
		this.name = name;
		this.type = type;
		this.description = description;
		this.minPriceCents = minPriceCents;
		this.maxPriceCents = maxPriceCents;
		this.active = true;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
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

	public ProductType getType() {
		return type;
	}

	public void setType(ProductType type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getMinPriceCents() {
		return minPriceCents;
	}

	public void setMinPriceCents(Integer minPriceCents) {
		this.minPriceCents = minPriceCents;
	}

	public Integer getMaxPriceCents() {
		return maxPriceCents;
	}

	public void setMaxPriceCents(Integer maxPriceCents) {
		this.maxPriceCents = maxPriceCents;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Product other)) {
			return false;
		}
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
