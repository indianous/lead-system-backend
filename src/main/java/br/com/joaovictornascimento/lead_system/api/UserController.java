package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.User;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('CREATE_USER')")
	public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal User currentUser) {
		return userService.create(request, currentUser);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('CREATE_USER')")
	public List<UserResponse> list() {
		return userService.findAll();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('CREATE_USER')")
	public UserResponse getById(@PathVariable UUID id) {
		return userService.findById(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('CREATE_USER')")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
		return userService.update(id, request);
	}

}
