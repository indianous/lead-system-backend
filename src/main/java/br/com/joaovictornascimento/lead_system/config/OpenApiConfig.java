package br.com.joaovictornascimento.lead_system.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@SecurityScheme(name = "publicApiKey", type = SecuritySchemeType.HTTP, scheme = "bearer",
		description = "API key do endpoint público de recepção de leads do site (POST /api/public/leads) — "
				+ "não confundir com o JWT de login de usuário.")
@Configuration
public class OpenApiConfig {

}
