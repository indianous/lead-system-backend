package br.com.joaovictornascimento.lead_system.api;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resourceName, UUID id) {
		super(resourceName + " não encontrado: " + id);
	}

}
