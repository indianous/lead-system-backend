package br.com.joaovictornascimento.lead_system.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	public ResponseEntity<Map<String, String>> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
	}

	@ExceptionHandler(LeadAccessDeniedException.class)
	public ResponseEntity<Map<String, String>> handleLeadAccessDenied(LeadAccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
	}

}
