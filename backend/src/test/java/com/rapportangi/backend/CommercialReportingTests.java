package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import com.rapportangi.backend.service.*;
import java.nio.file.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CommercialReportingTests {

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

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    reports.deleteAll();
    users.deleteAll();
    account("seller@test.com", "COMMERCIAL", "AZO");
    account("super@test.com", "SUPER_ADMIN", null);
    account("admin@test.com", "ADMIN", null);
    account("zehami@test.com", "COMMERCIAL", "ZEHAMI");
  }

  void account(String email, String role, String team) {
    var u = new User();
    u.setFirstName("Démonstration");
    u.setLastName(team == null ? "Direction" : team);
    u.setEmail(email);
    u.setPassword(passwords.encode("strong-test-password"));
    u.setTeam(team);
    u.setReferralCode(team == null ? null : "CODE-" + team);
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    users.save(u);
  }

  String signup() {
    return "{\"firstName\":\"Alice\",\"lastName\":\"Test\",\"email\":\"alice@test.com\",\"password\":\"long-secret-password\",\"team\":\"ZEHAMI\",\"referralCode\":\"FORGED\",\"roles\":[\"SUPER_ADMIN\"],\"enabled\":true}";
  }

  Long create(String email, String mode, String status) throws Exception {
    mvc
      .perform(
        post("/api/reports")
          .with(user(email).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload(mode, status, 0))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.statsSource").value("DECLARED"));
    return reports
      .findAll()
      .stream()
      .map(Report::getId)
      .max(Long::compare)
      .orElseThrow();
  }

  String payload(String mode, String status, long version) {
    return (
      "{\"title\":\"Bilan terrain à Abomey-Calavi\",\"content\":\"Visites et échanges avec les prospects.\",\"mode\":\"" +
      mode +
      "\",\"status\":\"" +
      status +
      "\",\"prospectsContacted\":12,\"registrations\":3,\"team\":\"ZEHAMI\",\"version\":" +
      version +
      ",\"statsSource\":\"APP_API\"}"
    );
  }

  long version(Long id) {
    return reports.findById(id).orElseThrow().getVersion();
  }

  void review(Long id, String action, String comment, int status)
    throws Exception {
    mvc
      .perform(
        post("/api/reports/" + id + "/review")
          .with(user("super@test.com").roles("SUPER_ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"action\":\"" +
              action +
              "\",\"comment\":\"" +
              comment +
              "\",\"version\":" +
              version(id) +
              "}"
          )
      )
      .andExpect(status().is(status));
  }

  @Test
  void publicSignupStaysPendingWithoutPrivilegeOrReferralCode()
    throws Exception {
    mvc
      .perform(
        post("/api/auth/register")
          .with(csrf())
          .contentType("application/json")
          .content(signup())
      )
      .andExpect(status().isCreated());
    var u = users.findByEmail("alice@test.com").orElseThrow();
    assertFalse(u.isEnabled());
    assertNull(u.getReferralCode());
    assertTrue(passwords.matches("long-secret-password", u.getPassword()));
    assertEquals(
      Set.of("COMMERCIAL"),
      u
        .getRoles()
        .stream()
        .map(Role::getName)
        .collect(java.util.stream.Collectors.toSet())
    );
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "alice@test.com")
          .param("password", "long-secret-password")
      )
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        put("/api/commercials/" + u.getId())
          .with(user("super@test.com").roles("SUPER_ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"team\":\"ZEHAMI\",\"referralCode\":\"Z-001\",\"enabled\":true}"
          )
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        post("/api/auth/login")
          .with(csrf())
          .param("email", "ALICE@test.com")
          .param("password", "long-secret-password")
      )
      .andExpect(status().isNoContent());
  }

  @Test
  void onlySuperAdminCanCreateCommercialAndCodesAreUniquePerTeam()
    throws Exception {
    mvc
      .perform(
        post("/api/commercials")
          .with(user("admin@test.com").roles("ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(signup())
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/commercials")
          .with(user("super@test.com").roles("SUPER_ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(signup())
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.enabled").value(true))
      .andExpect(jsonPath("$.password").doesNotExist());
    mvc
      .perform(
        post("/api/auth/register")
          .with(csrf())
          .contentType("application/json")
          .content(signup().replace("alice@test.com", "ALICE@test.com"))
      )
      .andExpect(status().isConflict());
    var id = users.findByEmail("alice@test.com").orElseThrow().getId();
    mvc
      .perform(
        put("/api/commercials/" + id)
          .with(user("super@test.com").roles("SUPER_ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"team\":\"AZO\",\"referralCode\":\"CODE-AZO\",\"enabled\":true}"
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void submissionReturnResubmissionAndConfirmationPreserveHistory()
    throws Exception {
    var id = create("seller@test.com", "STATS", "SUBMITTED");
    assertEquals("AZO", reports.findById(id).orElseThrow().getTeam()); // forged team ignored
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("STATS", "DRAFT", version(id)))
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        post("/api/reports/" + id + "/review")
          .with(user("admin@test.com").roles("ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content("{\"action\":\"CONFIRMED\",\"version\":0}")
      )
      .andExpect(status().isForbidden());
    review(id, "RETURNED", "", 400);
    review(id, "RETURNED", "Confirmez les inscriptions attribuées.", 200);
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("STATS", "SUBMITTED", 0))
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(payload("STATS", "SUBMITTED", version(id)))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.reviews.length()").value(1));
    review(id, "CONFIRMED", "Chiffres confirmés après échange.", 200);
    mvc
      .perform(
        get("/api/reports/" + id).with(
          user("seller@test.com").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("CONFIRMED"))
      .andExpect(jsonPath("$.reviews.length()").value(2));
    mvc
      .perform(
        delete("/api/reports/" + id)
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
      )
      .andExpect(status().isConflict());
  }

  @Test
  void statsModeAcceptsOptionalNotesAndNoApiDataIsInvented() throws Exception {
    mvc
      .perform(
        post("/api/reports")
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"title\":\"Statistiques\",\"mode\":\"STATS\",\"registrations\":2}"
          )
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.content").value(""));
    mvc
      .perform(
        get("/api/integrations/status").with(
          user("seller@test.com").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.configured").value(false));
    mvc
      .perform(
        post("/api/reports")
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            payload("STATS", "DRAFT", 0).replace(
              "\"statsSource\":\"APP_API\"",
              "\"syncStats\":true"
            )
          )
      )
      .andExpect(status().isServiceUnavailable());
    mvc
      .perform(
        post("/api/reports")
          .with(user("seller@test.com").roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            payload("STATS", "DRAFT", 0).replace(
              "\"registrations\":3",
              "\"registrations\":-1"
            )
          )
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void brandedPdfIsProtectedAndContainsCommercialTeamAndStats()
    throws Exception {
    for (var email : new String[] { "seller@test.com", "zehami@test.com" }) {
      var id = create(email, "STATS", "DRAFT");
      mvc
        .perform(
          get("/api/reports/" + id + "/pdf").with(
            user("admin@test.com").roles("COMMERCIAL")
          )
        )
        .andExpect(status().isNotFound());
      byte[] bytes = mvc
        .perform(
          get("/api/reports/" + id + "/pdf").with(
            user(email).roles("COMMERCIAL")
          )
        )
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
      try (var doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
        var text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        assertTrue(text.contains("ANGI DSN PRO SARL"));
        assertTrue(
          text.contains(email.startsWith("seller") ? "AZO" : "ZEHAMI")
        );
        assertTrue(text.contains("Inscriptions attribuées : 3"));
        assertTrue(text.contains("Démonstration"));
      }
      if (Boolean.getBoolean("pdf.qa")) Files.write(
        Path.of(
          "/tmp/rapport-" +
            (email.startsWith("seller") ? "azo" : "zehami") +
            ".pdf"
        ),
        bytes
      );
    }
  }

  @Test
  void longPdfPaginatesAndContainsFinalText() throws Exception {
    var id = create("seller@test.com", "WRITTEN", "DRAFT");
    var report = reports.findById(id).orElseThrow();
    report.setContent(
      "Compte rendu détaillé de la prospection, échanges et prochaines actions.\n".repeat(
        120
      ) + "FIN DU RAPPORT"
    );
    reports.saveAndFlush(report);
    byte[] bytes = mvc
      .perform(
        get("/api/reports/" + id + "/pdf").with(
          user("seller@test.com").roles("COMMERCIAL")
        )
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsByteArray();
    try (var doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      assertTrue(doc.getNumberOfPages() > 1);
      assertTrue(
        new org.apache.pdfbox.text.PDFTextStripper()
          .getText(doc)
          .contains("FIN DU RAPPORT")
      );
    }
    if (Boolean.getBoolean("pdf.qa")) Files.write(
      Path.of("/tmp/rapport-long.pdf"),
      bytes
    );
  }
}
