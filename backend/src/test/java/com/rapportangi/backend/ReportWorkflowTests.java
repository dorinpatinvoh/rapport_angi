package com.rapportangi.backend;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ReportWorkflowTests {

  @Autowired
  WebApplicationContext context;

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  PasswordEncoder passwords;

  MockMvc mvc;
  Long authorId;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    reports.deleteAll();
    users.deleteAll();
    authorId = account("commercial@angi.test", "COMMERCIAL", true);
    account("other@angi.test", "COMMERCIAL", true);
    account("admin@angi.test", "ADMIN", true);
    account("disabled@angi.test", "COMMERCIAL", false);
  }

  Long account(String email, String role, boolean enabled) {
    var u = new User();
    u.setFirstName("Test");
    u.setLastName(role);
    u.setEmail(email);
    u.setTeam("AZO");
    u.setPassword(passwords.encode("strong-test-password"));
    u.setEnabled(enabled);
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    return users.save(u).getId();
  }

  String payload(String status) {
    return (
      "{\"title\":\" Visite client \",\"content\":\"Compte rendu de visite\",\"status\":\"" +
      status +
      "\",\"authorId\":999999,\"version\":0}"
    );
  }

  Long create() throws Exception {
    mvc
      .perform(
        post("/api/reports")
          .with(user("commercial@angi.test").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("DRAFT"))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", startsWith("/api/reports/")))
      .andExpect(jsonPath("$.authorId").value(authorId.intValue()))
      .andExpect(jsonPath("$.title").value("Visite client"));
    return reports.findAll().get(0).getId();
  }

  @Test
  void anonymousCannotReadReports() throws Exception {
    mvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
  }

  @Test
  void csrfIsRequiredForWritesAndLogin() throws Exception {
    mvc
      .perform(
        post("/api/auth/login")
          .param("email", "commercial@angi.test")
          .param("password", "strong-test-password")
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/reports")
          .with(user("commercial@angi.test"))
          .contentType("application/json")
          .content(payload("DRAFT"))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void loginCreatesSessionAndLogoutInvalidatesIt() throws Exception {
    var result = mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "commercial@angi.test")
          .param("password", "strong-test-password")
      )
      .andExpect(status().isNoContent())
      .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertNotNull(session);
    mvc
      .perform(get("/api/auth/me").session(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("commercial@angi.test"))
      .andExpect(jsonPath("$.password").doesNotExist());
    mvc
      .perform(post("/api/auth/logout").session(session).with(csrf()))
      .andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
  }

  @Test
  void badCredentialsAndDisabledAccountsAreRejected() throws Exception {
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "commercial@angi.test")
          .param("password", "wrong")
      )
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "disabled@angi.test")
          .param("password", "strong-test-password")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void ownerCanSubmitAndCannotDeleteSubmitted() throws Exception {
    var id = create();
    mvc
      .perform(
        get("/api/reports/" + id).with(
          user("commercial@angi.test").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content").value("Compte rendu de visite"));
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user("commercial@angi.test").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("SUBMITTED"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("SUBMITTED"))
      .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    mvc
      .perform(
        delete("/api/reports/" + id)
          .with(user("commercial@angi.test").roles("COMMERCIAL"))
          .with(csrf())
      )
      .andExpect(status().isConflict());
    assertEquals(1, reports.count());
  }

  @Test
  void anotherCommercialCannotListReadUpdateOrDeleteReport() throws Exception {
    var id = create();
    mvc
      .perform(
        get("/api/reports").with(user("other@angi.test").roles("COMMERCIAL"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(0)));
    mvc
      .perform(
        get("/api/reports/" + id).with(
          user("other@angi.test").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isNotFound());
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user("other@angi.test").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("SUBMITTED"))
      )
      .andExpect(status().isNotFound());
    mvc
      .perform(
        delete("/api/reports/" + id)
          .with(user("other@angi.test").roles("COMMERCIAL"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());
    assertEquals(1, reports.count());
  }

  @Test
  void adminCanAccessAllReports() throws Exception {
    var id = create();
    mvc
      .perform(get("/api/reports").with(user("admin@angi.test").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)));
    mvc
      .perform(
        delete("/api/reports/" + id)
          .with(user("admin@angi.test").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNoContent());
  }

  @Test
  void invalidPayloadIsRejectedAndMissingReportIs404() throws Exception {
    for (var body : new String[] {
      payload("APPROVED"),
      "{\"title\":\" \",\"content\":\"text\"}",
      "{\"title\":\"valid\",\"content\":\" \"}",
    }) {
      mvc
        .perform(
          post("/api/reports")
            .with(user("commercial@angi.test").roles("COMMERCIAL"))
            .with(csrf())
            .contentType("application/json")
            .content(body)
        )
        .andExpect(status().isBadRequest());
    }
    mvc
      .perform(
        get("/api/reports/999999").with(
          user("commercial@angi.test").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isNotFound());
  }
}
