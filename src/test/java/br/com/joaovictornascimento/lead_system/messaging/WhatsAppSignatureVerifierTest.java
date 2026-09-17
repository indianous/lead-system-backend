package br.com.joaovictornascimento.lead_system.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WhatsAppSignatureVerifierTest {

	private static final String APP_SECRET = "test-app-secret";

	private final WhatsAppSignatureVerifier verifier = new WhatsAppSignatureVerifier(APP_SECRET);

	private static String sign(String secret, byte[] body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
	}

	@Test
	void validSignatureIsAccepted() throws Exception {
		byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

		assertThat(verifier.isValid(body, sign(APP_SECRET, body))).isTrue();
	}

	@Test
	void signatureComputedWithWrongSecretIsRejected() throws Exception {
		byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

		assertThat(verifier.isValid(body, sign("wrong-secret", body))).isFalse();
	}

	@Test
	void tamperedBodyIsRejected() throws Exception {
		byte[] originalBody = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
		String signature = sign(APP_SECRET, originalBody);
		byte[] tamperedBody = "{\"hello\":\"WORLD\"}".getBytes(StandardCharsets.UTF_8);

		assertThat(verifier.isValid(tamperedBody, signature)).isFalse();
	}

	@Test
	void missingHeaderIsRejected() {
		assertThat(verifier.isValid("body".getBytes(StandardCharsets.UTF_8), null)).isFalse();
	}

	@Test
	void malformedHeaderIsRejected() {
		assertThat(verifier.isValid("body".getBytes(StandardCharsets.UTF_8), "not-a-valid-signature")).isFalse();
	}

}
