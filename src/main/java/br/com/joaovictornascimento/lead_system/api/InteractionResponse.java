package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.InteractionType;
import java.time.Instant;
import java.util.UUID;

public record InteractionResponse(UUID id, UUID leadId, UUID userId, String userName, InteractionType type,
		String content, Instant createdAt) {
}
