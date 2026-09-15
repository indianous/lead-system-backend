package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.QualificationScore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record UpdateLeadRequest(@NotBlank String name, String phone, @Email String email, String initialMessage,
		@PositiveOrZero Integer estimatedBudgetCents, String desiredTimeline, QualificationScore qualificationScore,
		@NotNull UUID assignedUserId, List<UUID> productIds) {
}
