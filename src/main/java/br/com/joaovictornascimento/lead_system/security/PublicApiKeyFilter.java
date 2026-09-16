package br.com.joaovictornascimento.lead_system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica POST /api/public/leads com uma API key estática — o chamador é o backend do site
 * institucional (fora do escopo deste projeto), sem User/Role, então não passa pelo fluxo de
 * login normal (ver plano da Etapa 4).
 */
@Component
public class PublicApiKeyFilter extends OncePerRequestFilter {

	public static final String PUBLIC_API_ACCESS_AUTHORITY = "PUBLIC_API_ACCESS";

	private static final String PUBLIC_PATH_PREFIX = "/api/public/";

	private final String expectedKey;

	public PublicApiKeyFilter(@Value("${app.public-api.key}") String expectedKey) {
		this.expectedKey = expectedKey;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String provided = header.substring("Bearer ".length());
			if (matches(expectedKey, provided)) {
				var authentication = new UsernamePasswordAuthenticationToken(null, null,
						List.of(new SimpleGrantedAuthority(PUBLIC_API_ACCESS_AUTHORITY)));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}

	static boolean matches(String expected, String provided) {
		if (expected == null || provided == null) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
	}

}
