package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import br.com.joaovictornascimento.lead_system.domain.LeadType;
import br.com.joaovictornascimento.lead_system.domain.SearchSource;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

// qualificationScore não é aceito do cliente — é sempre calculado pelo QualificationService
// (Etapa 7), na criação e na atualização do lead.
public record CreateLeadRequest(@NotBlank String name, @NotNull LeadType leadType, String phone,
		@Email String email, String initialMessage, @PositiveOrZero Integer estimatedBudgetCents,
		String desiredTimeline, @NotNull UUID assignedUserId, List<UUID> productIds, Channel channel,
		SearchSource searchSource, String region, String searchSegment) {

	@AssertTrue(message = "Campos de origem inconsistentes com o leadType informado")
	public boolean isOriginConsistent() {
		if (leadType == null) {
			return true;
		}
		return switch (leadType) {
			case DIRECT_CONTACT -> channel != null && searchSource == null && region == null && searchSegment == null;
			case LOCAL_SEARCH -> searchSource != null && region != null && searchSegment != null && channel == null;
		};
	}

}
