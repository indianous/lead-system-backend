package br.com.joaovictornascimento.lead_system.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro usuário administrador. Nome de classe segue a convenção do Flyway
 * (V&lt;versão&gt;__Descrição), exigida mesmo para migrations Java registradas como bean Spring
 * (só assim o Flyway consegue derivar versão/descrição sem uma implementação direta de
 * {@code JavaMigration}). Sendo um bean, o Spring Boot injeta {@code JavaMigration}s
 * automaticamente no Flyway, o que permite ler o e-mail/senha de {@code application.properties}
 * (variável de ambiente) e gerar o hash com o mesmo {@link PasswordEncoder} usado pelo resto da
 * aplicação.
 */
@Component
public class V4__SeedInitialAdminUser extends BaseJavaMigration {

	private final PasswordEncoder passwordEncoder;

	private final String adminEmail;

	private final String adminPassword;

	public V4__SeedInitialAdminUser(PasswordEncoder passwordEncoder,
			@Value("${app.security.initial-admin.email}") String adminEmail,
			@Value("${app.security.initial-admin.password}") String adminPassword) {
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		UUID adminRoleId = findManagerAdministratorRoleId(connection);
		insertAdminUser(connection, adminRoleId);
	}

	private UUID findManagerAdministratorRoleId(Connection connection) throws Exception {
		try (PreparedStatement statement = connection
			.prepareStatement("SELECT id FROM roles WHERE name = 'Manager/Administrator'")) {
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new IllegalStateException("Papel 'Manager/Administrator' não encontrado (V3 não aplicada?)");
				}
				return (UUID) resultSet.getObject("id");
			}
		}
	}

	private void insertAdminUser(Connection connection, UUID adminRoleId) throws Exception {
		String sql = """
				INSERT INTO users (id, name, email, password_hash, role_id, active, created_by_id, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, TRUE, NULL, ?, ?)
				""";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			Timestamp now = Timestamp.from(Instant.now());
			statement.setObject(1, UUID.randomUUID());
			statement.setString(2, "Administrador");
			statement.setString(3, adminEmail);
			statement.setString(4, passwordEncoder.encode(adminPassword));
			statement.setObject(5, adminRoleId);
			statement.setTimestamp(6, now);
			statement.setTimestamp(7, now);
			statement.executeUpdate();
		}
	}

}
