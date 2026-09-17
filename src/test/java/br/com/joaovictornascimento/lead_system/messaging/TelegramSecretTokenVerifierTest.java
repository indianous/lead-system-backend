package br.com.joaovictornascimento.lead_system.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramSecretTokenVerifierTest {

	private final TelegramSecretTokenVerifier verifier = new TelegramSecretTokenVerifier("correct-token");

	@Test
	void correctTokenIsValid() {
		assertThat(verifier.isValid("correct-token")).isTrue();
	}

	@Test
	void wrongTokenIsInvalid() {
		assertThat(verifier.isValid("wrong-token")).isFalse();
	}

	@Test
	void nullTokenIsInvalid() {
		assertThat(verifier.isValid(null)).isFalse();
	}

	@Test
	void emptyTokenIsInvalid() {
		assertThat(verifier.isValid("")).isFalse();
	}

}
