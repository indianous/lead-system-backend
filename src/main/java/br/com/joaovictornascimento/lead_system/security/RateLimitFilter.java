package br.com.joaovictornascimento.lead_system.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting por IP para /api/public/**, mitigando abuso/spam nesse endpoint público (ver
 * plano da Etapa 4). Um Bucket4j em memória por IP — suficiente para uma instância única; sem
 * backend distribuído, não sobrevive a múltiplas instâncias da aplicação.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final String PUBLIC_PATH_PREFIX = "/api/public/";

	private final long capacity;

	private final long refillTokens;

	private final Duration refillDuration;

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	public RateLimitFilter(@Value("${app.public-api.rate-limit.capacity}") long capacity,
			@Value("${app.public-api.rate-limit.refill-tokens}") long refillTokens,
			@Value("${app.public-api.rate-limit.refill-duration-seconds}") long refillDurationSeconds) {
		this.capacity = capacity;
		this.refillTokens = refillTokens;
		this.refillDuration = Duration.ofSeconds(refillDurationSeconds);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), address -> newBucket());
		if (bucket.tryConsume(1)) {
			filterChain.doFilter(request, response);
			return;
		}
		// HttpServletResponse não define uma constante SC_TOO_MANY_REQUESTS (429).
		response.setStatus(429);
		response.setContentType("application/json");
		response.getWriter().write("{\"message\":\"Limite de requisições excedido, tente novamente mais tarde\"}");
	}

	private Bucket newBucket() {
		Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, refillDuration));
		return Bucket.builder().addLimit(limit).build();
	}

}
