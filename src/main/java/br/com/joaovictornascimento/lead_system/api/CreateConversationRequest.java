package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(@NotNull Channel channel) {

	@AssertTrue(message = "Canal não suportado pela central de mensagens (use META_WHATSAPP ou TELEGRAM)")
	public boolean isChannelSupported() {
		return channel == null || channel == Channel.META_WHATSAPP || channel == Channel.TELEGRAM;
	}

}
