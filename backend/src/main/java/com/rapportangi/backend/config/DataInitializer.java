package com.rapportangi.backend.config;

import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.RoleRepository;
import com.rapportangi.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;

@Configuration
public class DataInitializer implements CommandLineRunner {
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    public DataInitializer(RoleRepository roles, UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap.email:}") String email, @Value("${app.bootstrap.password:}") String password) {
        this.roles = roles; this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }
    @Override public void run(String... args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12 || password.length() > 72)
            throw new IllegalStateException("Bootstrap : fournir un email et un mot de passe de 12 à 72 caractères");
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            // Migration explicite du compte initial de l'ancien prototype, sans accepter de connexion en clair.
            var user = existing.get();
            if (password.equals(user.getPassword())) {
                user.setPassword(encoder.encode(password)); users.save(user);
            }
            return;
        }
        var admin = new User();
        admin.setFirstName("Administration"); admin.setLastName("ANGI");
        admin.setEmail(email); admin.setPassword(encoder.encode(password));
        admin.setRoles(Set.of(roles.findByName("SUPER_ADMIN").orElseThrow()));
        users.save(admin);
    }
}
