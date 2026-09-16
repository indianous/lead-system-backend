package br.com.joaovictornascimento.lead_system.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

// qualificationScore não é aceito do cliente — é sempre calculado pelo QualificationService
// (Etapa 7), na criação e na atualização do lead.
public record UpdateLeadRequest(@NotBlank String name, String phone, @Email String email, String initialMessage,
		@PositiveOrZero Integer estimatedBudgetCents, String desiredTimeline, @NotNull UUID assignedUserId,
		List<UUID> productIds) {
}
