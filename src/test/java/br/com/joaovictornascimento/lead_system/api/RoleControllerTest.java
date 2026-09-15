package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import br.com.joaovictornascimento.lead_system.security.JwtService;
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
class RoleControllerTest {

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

	@Test
	void listRolesWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
	}

	@Test
	void listRolesWithoutCreateUserPermissionReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-roles1@leadsystem.local", salesperson);

		mockMvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser)))
			.andExpect(status().isForbidden());
	}

	@Test
	void listRolesWithCreateUserPermissionReturnsSeededRoles() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-roles1@leadsystem.local", manager);

		mockMvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, tokenFor(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name=='Salesperson')]").exists())
			.andExpect(jsonPath("$[?(@.name=='Manager/Administrator')]").exists())
			.andExpect(jsonPath("$[?(@.name=='Salesperson')].permissions[0]").value("TRIGGER_PROSPECTING"));
	}

}
