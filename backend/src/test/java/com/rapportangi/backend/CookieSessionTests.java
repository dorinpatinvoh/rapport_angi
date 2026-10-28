package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// Isolation from Spring Security test's csrf() repository replacement in the other suite.
@SpringBootTest(
  properties = "app.allowed-origins=https://rapports.angidsnpro.com"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CookieSessionTests {

  @Autowired
  WebApplicationContext context;

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  PasswordEncoder passwords;

  private static final String FRONTEND = "https://rapports.angidsnpro.com";

  @Test
  void cookieCsrfTokenWorksForAngular() throws Exception {
    var u = new User();
    u.setFirstName("Cookie");
    u.setLastName("Test");
    u.setEmail("cookie@angi.test");
    u.setTeam("AZO");
    u.setPassword(passwords.encode("strong-test-password"));
    u.setRoles(Set.of(roles.findByName("COMMERCIAL").orElseThrow()));
    users.save(u);
    var mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    var result = mvc
      .perform(get("/api/auth/csrf").header("Origin", FRONTEND))
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
      .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andReturn();
    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(cookie);
    // CORS approval does not bypass CSRF for a state-changing request.
    mvc
      .perform(
        post("/api/auth/login")
          .header("Origin", FRONTEND)
          .cookie(cookie)
          .param("email", "cookie@angi.test")
          .param("password", "strong-test-password")
      )
      .andExpect(status().isForbidden());
    var login = mvc
      .perform(
        post("/api/auth/login")
          .header("Origin", FRONTEND)
          .cookie(cookie)
          .header("X-XSRF-TOKEN", cookie.getValue())
          .param("email", "cookie@angi.test")
          .param("password", "strong-test-password")
      )
      .andExpect(status().isNoContent())
      .andReturn();
    var session = (org.springframework.mock.web.MockHttpSession) login
      .getRequest()
      .getSession(false);
    var renewed = mvc
      .perform(
        get("/api/auth/csrf").header("Origin", FRONTEND).session(session)
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getCookie("XSRF-TOKEN");
    assertNotNull(renewed);
    assertNotEquals(cookie.getValue(), renewed.getValue());
    mvc
      .perform(
        post("/api/reports")
          .session(session)
          .cookie(renewed)
          .header("X-XSRF-TOKEN", renewed.getValue())
          .contentType("application/json")
          .content("{\"title\":\"Session réelle\",\"content\":\"Rapport\"}")
      )
      .andExpect(status().isCreated());
    mvc
      .perform(
        post("/api/auth/logout")
          .session(session)
          .cookie(renewed)
          .header("X-XSRF-TOKEN", renewed.getValue())
      )
      .andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
  }

  @Test
  void preflightAllowsOnlyTheConfiguredFrontend() throws Exception {
    var mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    mvc
      .perform(
        options("/api/reports")
          .header("Origin", FRONTEND)
          .header("Access-Control-Request-Method", "POST")
          .header("Access-Control-Request-Headers", "content-type,x-xsrf-token")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
      .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    for (String origin : java.util.List.of(
      "https://evil.example",
      "https://rapports.angidsnpro.com.evil.example",
      "null"
    )) {
      mvc
        .perform(
          options("/api/reports")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
        )
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
      mvc
        .perform(get("/api/auth/csrf").header("Origin", origin))
        .andExpect(status().isForbidden());
    }
  }
}
