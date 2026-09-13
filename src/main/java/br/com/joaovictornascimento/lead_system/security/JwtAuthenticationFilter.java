package br.com.joaovictornascimento.lead_system.security;

import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a request a partir do Bearer JWT. Não confia nas claims do token para as permissões:
 * busca o {@link User} atual no banco a cada request, para que desativação/troca de papel tenham
 * efeito imediato (ver plano da Etapa 1).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring("Bearer ".length());
			jwtService.parseUserId(token)
				.flatMap(userRepository::findById)
				.filter(User::isActive)
				.ifPresent(this::authenticate);
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(User user) {
		List<GrantedAuthority> authorities = user.getRole()
			.getPermissions()
			.stream()
			.map(permission -> new SimpleGrantedAuthority(permission.getKey()))
			.map(GrantedAuthority.class::cast)
			.toList();
		var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
