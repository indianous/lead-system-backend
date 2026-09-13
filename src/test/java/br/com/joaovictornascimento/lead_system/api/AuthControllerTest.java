package br.com.joaovictornascimento.lead_system.api;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

	private static final String ADMIN_EMAIL = "admin@leadsystem.local";

	private static final String ADMIN_PASSWORD = "ChangeMe123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private static String loginJson(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	@Test
	void loginWithValidAdminCredentialsReturnsToken() throws Exception {
		mockMvc
			.perform(post("/api/auth/login").contentType("application/json").content(loginJson(ADMIN_EMAIL, ADMIN_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value(not(emptyString())))
			.andExpect(jsonPath("$.id").value(not(emptyString())))
			.andExpect(jsonPath("$.name").value("Administrador"))
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
			.andExpect(jsonPath("$.role").value("Manager/Administrator"));
	}

	@Test
	void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
		mockMvc
			.perform(post("/api/auth/login").contentType("application/json")
				.content(loginJson(ADMIN_EMAIL, "wrong-password")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithUnknownEmailReturnsUnauthorized() throws Exception {
		mockMvc
			.perform(post("/api/auth/login").contentType("application/json")
				.content(loginJson("unknown@leadsystem.local", "whatever123")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithInactiveUserReturnsUnauthorized() throws Exception {
		var salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		var inactiveUser = new User("Inativo", "inativo@leadsystem.local", passwordEncoder.encode("somepassword1"),
				salesperson, null);
		inactiveUser.setActive(false);
		userRepository.save(inactiveUser);

		mockMvc
			.perform(post("/api/auth/login").contentType("application/json")
				.content(loginJson("inativo@leadsystem.local", "somepassword1")))
			.andExpect(status().isUnauthorized());
	}

}
