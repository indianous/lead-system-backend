package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.ProductType;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String name, ProductType type, String description, Integer minPriceCents,
		Integer maxPriceCents, boolean active, Instant createdAt) {
}
