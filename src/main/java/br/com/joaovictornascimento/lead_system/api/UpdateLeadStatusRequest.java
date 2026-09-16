package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.FunnelStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record UpdateLeadStatusRequest(@NotNull FunnelStatus newStatus, String reason) {

	@AssertTrue(message = "reason é obrigatório ao mover o lead para LOST")
	public boolean isReasonPresentWhenLost() {
		return newStatus != FunnelStatus.LOST || (reason != null && !reason.isBlank());
	}

}
