package br.com.joaovictornascimento.lead_system.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				.anyRequest()
				.authenticated())
			// Sem isso, o Spring Security responde 403 (não 401) tanto para request sem
			// autenticação quanto para autenticado sem permissão, pois o entry point padrão
			// quando não há httpBasic/formLogin configurado é Http403ForbiddenEntryPoint.
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, authException) -> response
					.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

}
