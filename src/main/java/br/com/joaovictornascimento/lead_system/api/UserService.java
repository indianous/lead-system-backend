package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Role;
import br.com.joaovictornascimento.lead_system.domain.RoleRepository;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public UserResponse create(CreateUserRequest request, User currentUser) {
		if (userRepository.existsByEmail(request.email())) {
			throw new EmailAlreadyInUseException(request.email());
		}
		Role role = findRoleOrThrow(request.roleId());
		String passwordHash = passwordEncoder.encode(request.password());
		User user = new User(request.name(), request.email(), passwordHash, role, currentUser);
		return toResponse(userRepository.save(user));
	}

	public List<UserResponse> findAll() {
		return userRepository.findAll().stream().map(this::toResponse).toList();
	}

	public UserResponse findById(UUID id) {
		return toResponse(findUserOrThrow(id));
	}

	public UserResponse update(UUID id, UpdateUserRequest request) {
		User user = findUserOrThrow(id);
		Role role = findRoleOrThrow(request.roleId());
		user.setName(request.name());
		user.setRole(role);
		user.setActive(request.active());
		return toResponse(userRepository.save(user));
	}

	private User findUserOrThrow(UUID id) {
		return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

	private Role findRoleOrThrow(UUID roleId) {
		return roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName(),
				user.isActive(), user.getCreatedAt());
	}

}
