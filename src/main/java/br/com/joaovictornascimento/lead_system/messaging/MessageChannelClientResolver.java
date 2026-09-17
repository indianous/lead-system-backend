package br.com.joaovictornascimento.lead_system.messaging;

import br.com.joaovictornascimento.lead_system.domain.Channel;
import org.springframework.stereotype.Component;

/**
 * Resolve o client de envio pelo canal — só META_WHATSAPP e TELEGRAM têm integração real nesta
 * etapa (o roadmap não pede Instagram/Messenger, ver plano da Etapa 8).
 */
@Component
public class MessageChannelClientResolver {

	private final WhatsAppMessageChannelClient whatsAppMessageChannelClient;

	private final TelegramMessageChannelClient telegramMessageChannelClient;

	public MessageChannelClientResolver(WhatsAppMessageChannelClient whatsAppMessageChannelClient,
			TelegramMessageChannelClient telegramMessageChannelClient) {
		this.whatsAppMessageChannelClient = whatsAppMessageChannelClient;
		this.telegramMessageChannelClient = telegramMessageChannelClient;
	}

	public MessageChannelClient resolve(Channel channel) {
		return switch (channel) {
			case META_WHATSAPP -> whatsAppMessageChannelClient;
			case TELEGRAM -> telegramMessageChannelClient;
			case META_INSTAGRAM, META_MESSENGER, WEBSITE ->
				throw new IllegalArgumentException("Canal sem integração de envio: " + channel);
		};
	}

}
