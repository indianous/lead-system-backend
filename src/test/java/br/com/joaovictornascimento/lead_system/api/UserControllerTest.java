package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	private String tokenFor(User user) {
		return "Bearer " + jwtService.generateToken(user.getId());
	}

	private User createUser(String name, String email, Role role) {
		return userRepository.save(new User(name, email, passwordEncoder.encode("somepassword1"), role, null));
	}

	private static String createUserJson(String name, String email, String password, UUID roleId) {
		return "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"roleId\":\"%s\"}".formatted(name, email,
				password, roleId);
	}

	private static String updateUserJson(String name, UUID roleId, boolean active) {
		return "{\"name\":\"%s\",\"roleId\":\"%s\",\"active\":%s}".formatted(name, roleId, active);
	}

	@Test
	void createUserWithoutTokenReturnsUnauthorized() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();

		mockMvc
			.perform(post("/api/users").contentType("application/json")
				.content(createUserJson("Novo", "novo1@leadsystem.local", "somepassword1", salesperson.getId())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createUserWithoutPermissionReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User plainSalesperson = createUser("Vendedor", "vendedor1@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/users").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(plainSalesperson))
				.content(createUserJson("Novo", "novo2@leadsystem.local", "somepassword1", salesperson.getId())))
			.andExpect(status().isForbidden());
	}

	@Test
	void createUserWithAdminTokenReturnsCreated() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor1@leadsystem.local", manager);
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();

		mockMvc
			.perform(post("/api/users").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(createUserJson("Novo Vendedor", "novo3@leadsystem.local", "somepassword1", salesperson.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("novo3@leadsystem.local"))
			.andExpect(jsonPath("$.roleName").value("Salesperson"));
	}

	@Test
	void createUserWithDuplicateEmailReturnsConflict() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor2@leadsystem.local", manager);
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		createUser("Existente", "duplicado@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/users").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(createUserJson("Outro", "duplicado@leadsystem.local", "somepassword1", salesperson.getId())))
			.andExpect(status().isConflict());
	}

	@Test
	void listAndGetUserWithAdminToken() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor3@leadsystem.local", manager);

		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/users/" + admin.getId()).header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("gestor3@leadsystem.local"));
	}

	@Test
	void getUnknownUserReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor4@leadsystem.local", manager);

		mockMvc
			.perform(get("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isNotFound());
	}

	@Test
	void deactivatingUserInvalidatesAlreadyIssuedToken() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor5@leadsystem.local", manager);
		String tokenIssuedBeforeDeactivation = tokenFor(admin);

		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, tokenIssuedBeforeDeactivation))
			.andExpect(status().isOk());

		mockMvc
			.perform(put("/api/users/" + admin.getId()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenIssuedBeforeDeactivation)
				.content(updateUserJson(admin.getName(), manager.getId(), false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, tokenIssuedBeforeDeactivation))
			.andExpect(status().isUnauthorized());
	}

}
