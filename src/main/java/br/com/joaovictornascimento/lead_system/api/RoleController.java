package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Permission;
import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

	private final RoleRepository roleRepository;

	public RoleController(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('CREATE_USER')")
	public List<RoleResponse> list() {
		return roleRepository.findAll().stream().map(this::toResponse).toList();
	}

	private RoleResponse toResponse(Role role) {
		List<String> permissions = role.getPermissions().stream().map(Permission::getKey).sorted().toList();
		return new RoleResponse(role.getId(), role.getName(), role.getDescription(), permissions);
	}

}
