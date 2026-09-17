package br.com.joaovictornascimento.lead_system.messaging;

/** Abstração de envio de mensagem para um canal de mensageria contínua (WhatsApp, Telegram). */
public interface MessageChannelClient {

	SendMessageResult send(String externalThreadId, String content);

}
