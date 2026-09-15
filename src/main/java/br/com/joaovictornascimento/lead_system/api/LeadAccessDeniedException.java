package br.com.joaovictornascimento.lead_system.api;

import java.util.UUID;

public class LeadAccessDeniedException extends RuntimeException {

	public LeadAccessDeniedException(UUID id) {
		super("Sem permissão para acessar o lead: " + id);
	}

}
