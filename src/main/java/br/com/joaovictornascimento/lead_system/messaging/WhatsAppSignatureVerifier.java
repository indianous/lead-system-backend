package br.com.joaovictornascimento.lead_system.messaging;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifica a assinatura que a Meta manda no header X-Hub-Signature-256 — HMAC-SHA256 calculado
 * sobre o corpo cru da requisição usando o App Secret como chave (formato "sha256=&lt;hex&gt;").
 * Diferente do Telegram, que não assina o corpo (ver {@link TelegramSecretTokenVerifier}).
 */
@Component
public class WhatsAppSignatureVerifier {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private static final String SIGNATURE_PREFIX = "sha256=";

	private final String appSecret;

	public WhatsAppSignatureVerifier(@Value("${app.messaging.whatsapp.app-secret}") String appSecret) {
		this.appSecret = appSecret;
	}

	public boolean isValid(byte[] rawBody, String signatureHeader) {
		if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
			return false;
		}
		String expectedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
		String computedHex = computeHmacHex(rawBody);
		return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
				computedHex.getBytes(StandardCharsets.UTF_8));
	}

	private String computeHmacHex(byte[] rawBody) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return HexFormat.of().formatHex(mac.doFinal(rawBody));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Falha ao calcular HMAC-SHA256", e);
		}
	}

}
