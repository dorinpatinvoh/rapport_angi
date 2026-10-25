package com.rapportangi.backend.config;

import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DataInitializer implements CommandLineRunner {

  private final RoleRepository roles;
  private final UserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final String email, password;
  private final boolean resetPassword;

  public DataInitializer(
    RoleRepository roles,
    UserRepository users,
    PasswordResetTokenRepository tokens,
    PasswordEncoder encoder,
    @Value("${app.bootstrap.email:}") String email,
    @Value("${app.bootstrap.password:}") String password,
    @Value("${app.bootstrap.reset-password:false}") boolean resetPassword
  ) {
    this.roles = roles;
    this.users = users;
    this.tokens = tokens;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
    this.resetPassword = resetPassword;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!resetPassword && email.isBlank() && password.isBlank()) return;
    if (
      email.isBlank() ||
      password.length() < 12 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) throw new IllegalStateException(
      "Bootstrap : fournir un email et un mot de passe d'au moins 12 caractères, limité à 72 octets UTF-8"
    );
    var existing = users.findByEmailIgnoreCase(email.strip());
    if (resetPassword) {
      var user = existing.orElseThrow(() ->
        new IllegalStateException(
          "Récupération : aucun compte ne correspond à cet email. Aucun compte n'a été créé."
        )
      );
      user = users.findLockedById(user.getId()).orElseThrow();
      if (
        !user.isEnabled() ||
        user
          .getRoles()
          .stream()
          .noneMatch(
            r ->
              r.getName().equals("SUPER_ADMIN") ||
              r.getName().equals("ROLE_SUPER_ADMIN")
          )
      ) throw new IllegalStateException(
        "Récupération réservée à un compte SUPER_ADMIN actif existant."
      );
      user.setPassword(encoder.encode(password));
      user.setSecurityVersion(user.getSecurityVersion() + 1);
      users.saveAndFlush(user);
      tokens.deleteByUserId(user.getId());
      System.out.println(
        ">>> Mot de passe SUPER_ADMIN réinitialisé. Redémarrez ensuite sans l'option de récupération."
      );
      return;
    }
    if (existing.isPresent()) {
      var user = existing.get();
      if (password.equals(user.getPassword())) {
        user.setPassword(encoder.encode(password));
        users.save(user);
      }
      return;
    }
    var admin = new User();
    admin.setFirstName("Administration");
    admin.setLastName("ANGI");
    admin.setEmail(email.strip());
    admin.setPassword(encoder.encode(password));
    admin.setRoles(Set.of(roles.findByName("SUPER_ADMIN").orElseThrow()));
    users.save(admin);
  }
}
