package br.com.joaovictornascimento.lead_system.api;

public class EmailAlreadyInUseException extends RuntimeException {

	public EmailAlreadyInUseException(String email) {
		super("Já existe um usuário com o e-mail " + email);
	}

}
