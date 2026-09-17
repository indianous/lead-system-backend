package br.com.joaovictornascimento.lead_system.messaging;

public record SendMessageResult(boolean success, String externalMessageId, String errorMessage) {

	public static SendMessageResult success(String externalMessageId) {
		return new SendMessageResult(true, externalMessageId, null);
	}

	public static SendMessageResult failure(String errorMessage) {
		return new SendMessageResult(false, null, errorMessage);
	}

}
