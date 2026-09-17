package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.messaging.TelegramSecretTokenVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/webhooks/telegram")
public class TelegramWebhookController {

	private final TelegramSecretTokenVerifier secretTokenVerifier;

	private final TelegramWebhookService webhookService;

	public TelegramWebhookController(TelegramSecretTokenVerifier secretTokenVerifier,
			TelegramWebhookService webhookService) {
		this.secretTokenVerifier = secretTokenVerifier;
		this.webhookService = webhookService;
	}

	@PostMapping
	public ResponseEntity<Void> receive(
			@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
			@RequestBody JsonNode update) {
		if (!secretTokenVerifier.isValid(secretToken)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		webhookService.process(update);
		return ResponseEntity.ok().build();
	}

}
