package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.messaging.WhatsAppSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

	private final WhatsAppSignatureVerifier signatureVerifier;

	private final WhatsAppWebhookService webhookService;

	private final ObjectMapper objectMapper;

	private final String webhookVerifyToken;

	public WhatsAppWebhookController(WhatsAppSignatureVerifier signatureVerifier, WhatsAppWebhookService webhookService,
			ObjectMapper objectMapper,
			@Value("${app.messaging.whatsapp.webhook-verify-token}") String webhookVerifyToken) {
		this.signatureVerifier = signatureVerifier;
		this.webhookService = webhookService;
		this.objectMapper = objectMapper;
		this.webhookVerifyToken = webhookVerifyToken;
	}

	@GetMapping
	public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
			@RequestParam("hub.verify_token") String verifyToken, @RequestParam("hub.challenge") String challenge) {
		if ("subscribe".equals(mode) && webhookVerifyToken.equals(verifyToken)) {
			return ResponseEntity.ok(challenge);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	@PostMapping
	public ResponseEntity<Void> receive(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody byte[] rawBody) {
		if (!signatureVerifier.isValid(rawBody, signature)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		JsonNode payload = objectMapper.readTree(rawBody);
		webhookService.process(payload);
		return ResponseEntity.ok().build();
	}

}
