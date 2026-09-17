package br.com.joaovictornascimento.lead_system.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifica o header X-Telegram-Bot-Api-Secret-Token contra o secret_token configurado uma vez via
 * setWebhook — o Telegram não assina o corpo da requisição, só ecoa esse token fixo em toda
 * chamada, então a checagem é uma comparação simples (em tempo constante).
 */
@Component
public class TelegramSecretTokenVerifier {

	private final String expectedToken;

	public TelegramSecretTokenVerifier(
			@Value("${app.messaging.telegram.webhook-secret-token}") String expectedToken) {
		this.expectedToken = expectedToken;
	}

	public boolean isValid(String providedToken) {
		if (expectedToken == null || providedToken == null) {
			return false;
		}
		return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
				providedToken.getBytes(StandardCharsets.UTF_8));
	}

}
