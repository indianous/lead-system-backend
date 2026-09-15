package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.QualificationScore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LeadResponse(UUID id, String name, LeadType leadType, String phone, String email,
		String initialMessage, Integer estimatedBudgetCents, String desiredTimeline,
		QualificationScore qualificationScore, FunnelStatus funnelStatus, String lossReason,
		LeadOriginResponse origin, UUID assignedUserId, String assignedUserName,
		List<ProductResponse> productsOfInterest, Instant createdAt, Instant updatedAt) {
}
