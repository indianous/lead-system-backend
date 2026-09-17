package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(UUID id, UUID leadId, Channel channel, String externalThreadId,
		ConversationStatus status, Instant createdAt, Instant updatedAt) {
}
