package br.com.joaovictornascimento.lead_system.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
			PublicApiKeyFilter publicApiKeyFilter, RateLimitFilter rateLimitFilter,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// "/error": OncePerRequestFilter (JwtAuthenticationFilter) não roda em
				// dispatch de erro por padrão (shouldNotFilterErrorDispatch()==true), então
				// o forward interno do container para "/error" chega sem autenticação — sem
				// isso aqui, toda resposta 4xx/5xx (ex.: 400 de Bean Validation) virava 401
				// vazio nesse forward, mascarando o status original (bug só visível fora do
				// MockMvc, que não reproduz o forward de erro do container real).
				.requestMatchers("/api/auth/login", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				// Webhooks da Meta/Telegram — chamados pelo provedor, não por um usuário nem
				// pela API key pública; a segurança real é a verificação de assinatura/secret
				// token feita dentro dos próprios controllers (ver plano da Etapa 8).
				.requestMatchers("/api/webhooks/whatsapp", "/api/webhooks/telegram")
				.permitAll()
				// Endpoint STOMP do chat em tempo real — o handshake HTTP não carrega o JWT (a API
				// WebSocket nativa do navegador não permite headers customizados no upgrade); a
				// autenticação de verdade acontece dentro do frame STOMP CONNECT, num
				// ChannelInterceptor (ver plano da Etapa 9).
				.requestMatchers("/ws/**")
				.permitAll()
				// Endpoint público (site institucional) — autenticado por API key, não por JWT
				// de usuário; a authority é concedida pelo PublicApiKeyFilter.
				.requestMatchers("/api/public/leads")
				.hasAuthority(PublicApiKeyFilter.PUBLIC_API_ACCESS_AUTHORITY)
				.anyRequest()
				.authenticated())
			// Sem isso, o Spring Security responde 403 (não 401) tanto para request sem
			// autenticação quanto para autenticado sem permissão, pois o entry point padrão
			// quando não há httpBasic/formLogin configurado é Http403ForbiddenEntryPoint.
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, authException) -> response
					.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
			// Rate limit roda primeiro (mais barato, limita até tentativas de força bruta da
			// API key); os dois mecanismos de autenticação (JWT de usuário / API key pública)
			// não interferem entre si — cada um só atua no seu próprio conjunto de rotas.
			// Ordem das chamadas importa: um filtro custom só pode ser usado como âncora
			// (segundo argumento) depois de ele mesmo já ter sido registrado relativo a um
			// filtro padrão do Spring Security — por isso jwtAuthenticationFilter entra antes
			// de ser usado como âncora do rateLimitFilter.
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
			.addFilterBefore(publicApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

}
