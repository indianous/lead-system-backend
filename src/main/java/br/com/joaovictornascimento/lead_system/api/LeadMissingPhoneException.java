package br.com.joaovictornascimento.lead_system.api;

import java.util.UUID;

public class LeadMissingPhoneException extends RuntimeException {

	public LeadMissingPhoneException(UUID leadId) {
		super("Lead sem telefone cadastrado, não é possível iniciar uma conversa de WhatsApp: " + leadId);
	}

}
