package br.com.joaovictornascimento.lead_system.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.joaovictornascimento.lead_system.config.TestcontainersConfiguration;
import br.com.joaovictornascimento.lead_system.domain.Product;
import br.com.joaovictornascimento.lead_system.domain.ProductRepository;
import br.com.joaovictornascimento.lead_system.domain.ProductType;
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
class ProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

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

	private static String createProductJson(String name, Integer minPriceCents, Integer maxPriceCents) {
		return "{\"name\":\"%s\",\"type\":\"READY_MADE\",\"description\":\"desc\",\"minPriceCents\":%s,\"maxPriceCents\":%s}"
			.formatted(name, minPriceCents, maxPriceCents);
	}

	private static String updateProductJson(String name, boolean active) {
		return "{\"name\":\"%s\",\"type\":\"CUSTOM\",\"description\":\"nova desc\",\"minPriceCents\":100,\"maxPriceCents\":200,\"active\":%s}"
			.formatted(name, active);
	}

	@Test
	void listProductsWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
	}

	@Test
	void listProductsWithAnyAuthenticatedUserReturnsOk() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-produtos1@leadsystem.local", salesperson);

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser)))
			.andExpect(status().isOk());
	}

	@Test
	void createProductWithoutEditCatalogReturnsForbidden() throws Exception {
		Role salesperson = roleRepository.findByName("Salesperson").orElseThrow();
		User salespersonUser = createUser("Vendedor", "vendedor-produtos2@leadsystem.local", salesperson);

		mockMvc
			.perform(post("/api/products").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(salespersonUser))
				.content(createProductJson("Site institucional", 50000, 100000)))
			.andExpect(status().isForbidden());
	}

	@Test
	void createProductWithEditCatalogReturnsCreated() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-produtos1@leadsystem.local", manager);

		mockMvc
			.perform(post("/api/products").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(createProductJson("Site institucional", 50000, 100000)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Site institucional"))
			.andExpect(jsonPath("$.type").value("READY_MADE"));
	}

	@Test
	void createProductWithMinPriceGreaterThanMaxPriceReturnsBadRequest() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-produtos2@leadsystem.local", manager);

		mockMvc
			.perform(post("/api/products").contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(createProductJson("Produto inválido", 100000, 50000)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void updateProductWithEditCatalogUpdatesFieldsAndAllowsDeactivating() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-produtos3@leadsystem.local", manager);
		Product product = productRepository
			.save(new Product("Produto antigo", ProductType.READY_MADE, "desc antiga", 1000, 2000));

		mockMvc
			.perform(put("/api/products/" + product.getId()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(updateProductJson("Produto atualizado", false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Produto atualizado"))
			.andExpect(jsonPath("$.type").value("CUSTOM"))
			.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void updateUnknownProductReturnsNotFound() throws Exception {
		Role manager = roleRepository.findByName("Manager/Administrator").orElseThrow();
		User admin = createUser("Gestor", "gestor-produtos4@leadsystem.local", manager);

		mockMvc
			.perform(put("/api/products/" + UUID.randomUUID()).contentType("application/json")
				.header(HttpHeaders.AUTHORIZATION, tokenFor(admin))
				.content(updateProductJson("Produto", true)))
			.andExpect(status().isNotFound());
	}

}
