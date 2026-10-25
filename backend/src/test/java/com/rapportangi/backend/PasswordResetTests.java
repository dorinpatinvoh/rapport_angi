package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
  properties = {
    "spring.mail.host=smtp.test",
    "app.mail.from=angi@example.test",
    "app.public-url=http://localhost:4200",
  }
)
@org.springframework.test.annotation.DirtiesContext(
  classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
)
@org.springframework.context.annotation.Import(
  PasswordResetTests.MailConfig.class
)
class PasswordResetTests {

  @Autowired
  WebApplicationContext context;

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  PasswordResetTokenRepository tokens;

  @Autowired
  PasswordEncoder encoder;

  @Autowired
  RecordingSender mail;

  MockMvc mvc;
  Long id;

  @BeforeEach
  void setup() {
    mail.messages.clear();
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    tokens.deleteAll();
    reports.deleteAll();
    users.deleteAll();
    var u = new User();
    u.setFirstName("Reset");
    u.setLastName("Test");
    u.setEmail("reset@example.test");
    u.setTeam("AZO");
    u.setPassword(encoder.encode("old-test-password"));
    u.setRoles(Set.of(roles.findByName("COMMERCIAL").orElseThrow()));
    id = users.save(u).getId();
  }

  String request() throws Exception {
    var result = mvc
      .perform(
        post("/api/auth/forgot-password")
          .with(csrf())
          .contentType("application/json")
          .content("{\"email\":\"reset@example.test\"}")
      )
      .andExpect(status().isOk())
      .andReturn();
    assertFalse(result.getResponse().getContentAsString().contains("token"));
    var text = mail.messages.get(0).getText();
    assertNotNull(text);
    return text.split("#token=")[1].split("\\s")[0];
  }

  @Test
  void emailedTokenIsSingleUseAndInvalidatesExistingSession() throws Exception {
    var login = mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "reset@example.test")
          .param("password", "old-test-password")
      )
      .andExpect(status().isNoContent())
      .andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    String token = request();
    String body =
      "{\"token\":\"" + token + "\",\"password\":\"new-test-password\"}";
    mvc
      .perform(
        post("/api/auth/reset-password")
          .with(csrf())
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isOk());
    assertEquals(0, tokens.count());
    mvc
      .perform(get("/api/auth/me").session(session))
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/auth/reset-password")
          .with(csrf())
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "reset@example.test")
          .param("password", "old-test-password")
      )
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "reset@example.test")
          .param("password", "new-test-password")
      )
      .andExpect(status().isNoContent());
  }

  @Test
  void unknownEmailGetsSameMessageAndCooldownPreventsRepeatedMail()
    throws Exception {
    request();
    mvc
      .perform(
        post("/api/auth/forgot-password")
          .with(csrf())
          .contentType("application/json")
          .content("{\"email\":\"reset@example.test\"}")
      )
      .andExpect(status().isOk());
    assertEquals(1, mail.messages.size());
    mvc
      .perform(
        post("/api/auth/forgot-password")
          .with(csrf())
          .contentType("application/json")
          .content("{\"email\":\"unknown@example.test\"}")
      )
      .andExpect(status().isOk())
      .andExpect(
        jsonPath("$.message").value(
          org.hamcrest.Matchers.startsWith("Si un compte actif")
        )
      );
  }

  @Test
  void expiredAndForgedTokensAreRejected() throws Exception {
    String raw = "expired-token";
    String hash = java.util.HexFormat.of().formatHex(
      java.security.MessageDigest.getInstance("SHA-256").digest(
        raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      )
    );
    tokens.save(
      new PasswordResetToken(
        users.findById(id).orElseThrow(),
        hash,
        Instant.now().minusSeconds(10)
      )
    );
    for (String token : new String[] { raw, "made-up-token" })
      mvc
        .perform(
          post("/api/auth/reset-password")
            .with(csrf())
            .contentType("application/json")
            .content(
              "{\"token\":\"" +
                token +
                "\",\"password\":\"another-test-password\"}"
            )
        )
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlySuperAdminMayResetCommercialWithoutEmail() throws Exception {
    String body = "{\"password\":\"admin-reset-password\"}";
    mvc
      .perform(
        post("/api/commercials/" + id + "/password")
          .with(user("reset@example.test").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/commercials/" + id + "/password")
          .with(user("super@example.test").roles("SUPER_ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isOk());
    assertTrue(
      encoder.matches(
        "admin-reset-password",
        users.findById(id).orElseThrow().getPassword()
      )
    );
  }

  static class RecordingSender
    extends org.springframework.mail.javamail.JavaMailSenderImpl
  {

    final java.util.List<SimpleMailMessage> messages =
      new java.util.ArrayList<>();

    @Override
    public void send(SimpleMailMessage message) {
      messages.add(new SimpleMailMessage(message));
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class MailConfig {

    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    RecordingSender recordingSender() {
      return new RecordingSender();
    }
  }
}
