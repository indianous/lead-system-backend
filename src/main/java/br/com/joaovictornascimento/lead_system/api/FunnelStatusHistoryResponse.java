package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import java.time.Instant;
import java.util.UUID;

public record FunnelStatusHistoryResponse(UUID id, FunnelStatus previousStatus, FunnelStatus newStatus, UUID userId,
		String userName, String reason, Instant changedAt) {
}
