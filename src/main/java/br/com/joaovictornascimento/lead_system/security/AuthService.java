package br.com.joaovictornascimento.lead_system.security;

import br.com.joaovictornascimento.lead_system.api.InvalidCredentialsException;
import br.com.joaovictornascimento.lead_system.api.LoginResponse;
import br.com.joaovictornascimento.lead_system.domain.User;
import br.com.joaovictornascimento.lead_system.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public LoginResponse login(String email, String rawPassword) {
		User user = userRepository.findByEmail(email)
			.filter(User::isActive)
			.filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
			.orElseThrow(InvalidCredentialsException::new);
		String token = jwtService.generateToken(user.getId());
		return new LoginResponse(user.getId(), user.getName(), user.getEmail(), token, user.getRole().getName());
	}

}
