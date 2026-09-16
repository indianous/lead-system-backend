package br.com.joaovictornascimento.lead_system.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicApiKeyFilterTest {

	@Test
	void matchesReturnsTrueForTheCorrectKey() {
		assertThat(PublicApiKeyFilter.matches("correct-key", "correct-key")).isTrue();
	}

	@Test
	void matchesReturnsFalseForAWrongKey() {
		assertThat(PublicApiKeyFilter.matches("correct-key", "wrong-key")).isFalse();
	}

	@Test
	void matchesReturnsFalseForAnEmptyKey() {
		assertThat(PublicApiKeyFilter.matches("correct-key", "")).isFalse();
	}

	@Test
	void matchesReturnsFalseWhenProvidedIsNull() {
		assertThat(PublicApiKeyFilter.matches("correct-key", null)).isFalse();
	}

	@Test
	void matchesReturnsFalseWhenExpectedIsNull() {
		assertThat(PublicApiKeyFilter.matches(null, "anything")).isFalse();
	}

}
