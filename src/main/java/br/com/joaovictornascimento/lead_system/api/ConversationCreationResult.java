package br.com.joaovictornascimento.lead_system.api;

/**
 * Uma das duas coisas sempre acontece ao criar uma "conversa": ou a Conversation nasce na hora
 * (WhatsApp), ou só um deep link do Telegram é devolvido (a Conversation nasce depois, quando o
 * lead manda /start pelo link — ver plano da Etapa 8).
 */
public record ConversationCreationResult(ConversationResponse conversation, String telegramDeepLink) {

	public static ConversationCreationResult ofConversation(ConversationResponse conversation) {
		return new ConversationCreationResult(conversation, null);
	}

	public static ConversationCreationResult ofTelegramDeepLink(String deepLink) {
		return new ConversationCreationResult(null, deepLink);
	}

}
