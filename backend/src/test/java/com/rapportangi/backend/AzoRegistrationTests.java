package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AzoRegistrationTests {

  @Autowired
  WebApplicationContext context;

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  EntityManager em;

  MockMvc mvc;
  User azo, zehami, other;
  String entry =
    "{\"firstName\":\" Élise \" ,\"lastName\":\" Dossou \" ,\"profileCode\":\"SPEEDER_MOTO_ESSENCE\"}";

  User account(String name, String team) {
    var u = new User();
    u.setFirstName(name);
    u.setLastName("Test");
    u.setEmail(name + "@registrations.test");
    u.setPassword("unused-test-hash");
    u.setTeam(team);
    u.setRoles(Set.of(roles.findByName("COMMERCIAL").orElseThrow()));
    return users.saveAndFlush(u);
  }

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    azo = account("azo-list", "AZO");
    zehami = account("zehami-list", "ZEHAMI");
    other = account("other-list", "AZO");
  }

  String body(String entries, String mode) {
    return (
      "{\"title\":\"Inscriptions terrain\",\"content\":\"Visites et inscriptions\",\"mode\":\"" +
      mode +
      "\",\"registrations\":7,\"profileCounts\":{},\"azoRegistrations\":" +
      entries +
      "}"
    );
  }

  Long create(String entries, String mode) throws Exception {
    mvc
      .perform(
        post("/api/reports")
          .with(user(azo.getEmail()).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(body(entries, mode))
      )
      .andExpect(status().isCreated());
    return reports
      .findByAuthorIdOrderByCreatedAtDesc(azo.getId())
      .get(0)
      .getId();
  }

  @Test
  void namesAndProfileSurviveDatabaseReloadAndCorrectionWithoutChangingCounters()
    throws Exception {
    Long id = create(
      "[" + entry + "," + entry.replace("SPEEDER_MOTO_ESSENCE", "CLIENT") + "]",
      "WRITTEN"
    );
    em.flush();
    em.clear();
    mvc
      .perform(
        get("/api/reports/" + id).with(user(azo.getEmail()).roles("COMMERCIAL"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.azoRegistrations.length()").value(2))
      .andExpect(jsonPath("$.azoRegistrations[0].firstName").value("Élise"))
      .andExpect(jsonPath("$.azoRegistrations[0].lastName").value("Dossou"))
      .andExpect(
        jsonPath("$.azoRegistrations[0].profileCode").value(
          "SPEEDER_MOTO_ESSENCE"
        )
      )
      .andExpect(jsonPath("$.registrations").value(0));
    long version = reports.findById(id).orElseThrow().getVersion();
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user(azo.getEmail()).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            body(
              "[" +
                entry
                  .replace("Élise", "Chloé")
                  .replace("SPEEDER_MOTO_ESSENCE", "AGENCE_PRO") +
                "]",
              "STATS"
            ).replace("\"title\":", "\"version\":" + version + ",\"title\":")
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.azoRegistrations[0].firstName").value("Chloé"))
      .andExpect(
        jsonPath("$.azoRegistrations[0].profileCode").value("AGENCE_PRO")
      )
      .andExpect(jsonPath("$.registrations").value(7));
    em.flush();
    em.clear();
    assertEquals(
      1,
      reports.findById(id).orElseThrow().getAzoRegistrations().size()
    );
    mvc
      .perform(
        get("/api/reports/" + id).with(
          user(other.getEmail()).roles("COMMERCIAL")
        )
      )
      .andExpect(status().isNotFound());
    mvc
      .perform(
        get("/api/reports/" + id + "/pdf").with(
          user(other.getEmail()).roles("COMMERCIAL")
        )
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void existingClientsCanOmitListAndExplicitEmptyListRemovesIt()
    throws Exception {
    Long id = create("[" + entry + "]", "STATS");
    long version = reports.findById(id).orElseThrow().getVersion();
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user(azo.getEmail()).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"title\":\"Ancien client API\",\"content\":\"Rapport\",\"version\":" +
              version +
              "}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.azoRegistrations.length()").value(1));
    version = reports.findById(id).orElseThrow().getVersion();
    mvc
      .perform(
        put("/api/reports/" + id)
          .with(user(azo.getEmail()).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(
            body("[]", "STATS").replace(
              "\"title\":",
              "\"version\":" + version + ",\"title\":"
            )
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.azoRegistrations.length()").value(0));
    em.flush();
    em.clear();
    assertTrue(
      reports.findById(id).orElseThrow().getAzoRegistrations().isEmpty()
    );
  }

  @Test
  void validatesEveryRowAndRejectsListForZehami() throws Exception {
    for (String invalid : List.of(
      "[null]",
      "[" + entry.replace("Élise", " ") + "]",
      "[" + entry.replace("Dossou", "x".repeat(101)) + "]",
      "[" + entry.replace("SPEEDER_MOTO_ESSENCE", "FORGED") + "]",
      "[" + String.join(",", Collections.nCopies(501, entry)) + "]"
    )) {
      mvc
        .perform(
          post("/api/reports")
            .with(user(azo.getEmail()).roles("COMMERCIAL"))
            .with(csrf())
            .contentType("application/json")
            .content(body(invalid, "STATS"))
        )
        .andExpect(status().isBadRequest());
    }
    mvc
      .perform(
        post("/api/reports")
          .with(user(zehami.getEmail()).roles("COMMERCIAL"))
          .with(csrf())
          .contentType("application/json")
          .content(body("[" + entry + "]", "WRITTEN"))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void pdfIncludesAllNamesAndProfilesAcrossPages() throws Exception {
    var entries = new ArrayList<String>();
    for (int i = 1; i <= 60; i++) entries.add(
      entry.replace("Élise", "Élise " + i)
    );
    Long id = create("[" + String.join(",", entries) + "]", "STATS");
    byte[] bytes = mvc
      .perform(
        get("/api/reports/" + id + "/pdf").with(
          user(azo.getEmail()).roles("COMMERCIAL")
        )
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsByteArray();
    try (var doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      var text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
      assertTrue(text.contains("PERSONNES INSCRITES SUR AZO"));
      assertTrue(text.contains("Nom : Dossou | Prénom : Élise 60"));
      assertTrue(text.contains("Profil : Speeder moto à essence"));
      assertTrue(doc.getNumberOfPages() > 1);
    }
  }
}
