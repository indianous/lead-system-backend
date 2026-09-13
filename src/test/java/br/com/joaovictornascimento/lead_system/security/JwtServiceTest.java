package br.com.joaovictornascimento.lead_system.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String SECRET = "test-secret-key-for-jwt-signing-not-for-production-use-0123456789";

	private final JwtService jwtService = new JwtService(SECRET, 60);

	@Test
	void generatesTokenAndExtractsUserId() {
		UUID userId = UUID.randomUUID();

		String token = jwtService.generateToken(userId);

		assertThat(jwtService.parseUserId(token)).contains(userId);
	}

	@Test
	void rejectsExpiredToken() {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
		Instant past = Instant.now().minusSeconds(3600);
		String expiredToken = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(past.minusSeconds(60)))
			.expiration(Date.from(past))
			.signWith(key)
			.compact();

		assertThat(jwtService.parseUserId(expiredToken)).isEmpty();
	}

	@Test
	void rejectsTokenWithInvalidSignature() {
		SecretKey otherKey = Keys.hmacShaKeyFor("another-completely-different-secret-key-0123456789012345".getBytes());
		String tokenSignedWithOtherKey = Jwts.builder()
			.subject(UUID.randomUUID().toString())
			.issuedAt(new Date())
			.expiration(Date.from(Instant.now().plusSeconds(3600)))
			.signWith(otherKey)
			.compact();

		assertThat(jwtService.parseUserId(tokenSignedWithOtherKey)).isEmpty();
	}

}
