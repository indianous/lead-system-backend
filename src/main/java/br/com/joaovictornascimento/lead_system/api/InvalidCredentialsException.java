package br.com.joaovictornascimento.lead_system.api;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("E-mail ou senha inválidos");
	}

}
