package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.rapportangi.backend.config.DataInitializer;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SuperAdminRecoveryTests {

  @Autowired
  RoleRepository roles;

  @Autowired
  UserRepository users;

  @Autowired
  PasswordResetTokenRepository tokens;

  @Autowired
  PasswordEncoder encoder;

  User account(String email, String role) {
    var u = new User();
    u.setFirstName("Recovery");
    u.setLastName("Test");
    u.setEmail(email);
    u.setPassword(encoder.encode("previous-test-password"));
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    return users.saveAndFlush(u);
  }

  DataInitializer runner(String email, boolean reset) {
    return new DataInitializer(
      roles,
      users,
      tokens,
      encoder,
      email,
      "replacement-test-password",
      reset
    );
  }

  @Test
  void explicitRecoveryChangesExistingAdminAndRevokesTokens() {
    var u = account("recovery@example.test", "SUPER_ADMIN");
    tokens.saveAndFlush(
      new PasswordResetToken(u, "1".repeat(64), Instant.now().plusSeconds(900))
    );
    runner("RECOVERY@example.test", true).run();
    assertTrue(
      encoder.matches(
        "replacement-test-password",
        users.findById(u.getId()).orElseThrow().getPassword()
      )
    );
    assertEquals(1, u.getSecurityVersion());
    assertEquals(
      0,
      tokens
        .findAll()
        .stream()
        .filter(t -> t.getUser().getId().equals(u.getId()))
        .count()
    );
  }

  @Test
  void normalBootstrapDoesNotResetExistingHashedPassword() {
    var u = account("unchanged@example.test", "SUPER_ADMIN");
    String hash = u.getPassword();
    runner(u.getEmail(), false).run();
    assertEquals(hash, u.getPassword());
    assertEquals(0, u.getSecurityVersion());
  }

  @Test
  void recoveryRejectsCommercialAndUnknownAccount() {
    var u = account("commercial-recovery@example.test", "COMMERCIAL");
    assertThrows(IllegalStateException.class, () ->
      runner(u.getEmail(), true).run()
    );
    assertThrows(IllegalStateException.class, () ->
      runner("missing-recovery@example.test", true).run()
    );
    assertTrue(users.findByEmail("missing-recovery@example.test").isEmpty());
  }
}
