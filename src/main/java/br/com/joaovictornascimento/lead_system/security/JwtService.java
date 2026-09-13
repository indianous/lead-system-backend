package br.com.joaovictornascimento.lead_system.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

	private final SecretKey key;

	private final Duration expiration;

	public JwtService(@Value("${app.security.jwt.secret}") String secret,
			@Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("app.security.jwt.secret não configurado");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(expirationMinutes);
	}

	public String generateToken(UUID userId) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(userId.toString())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(expiration)))
			.signWith(key)
			.compact();
	}

	public Optional<UUID> parseUserId(String token) {
		try {
			String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
			return Optional.of(UUID.fromString(subject));
		}
		catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
