package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.dto.CommercialRequest;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import com.rapportangi.backend.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class WorkspacePolicyTests {

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  AccountService accounts;

  @Autowired
  SalesManagementService sales;

  @Autowired
  CommunicationService comms;

  @Autowired
  ReportService reportService;

  @Autowired
  WebApplicationContext context;

  User root, bj, ng, sellerBj, sellerNg, sellerZehami, supervisorBj, supervisorNg;
  MockMvc mvc;

  User user(String name, String role, String country, String team) {
    var u = new User();
    u.setFirstName(name);
    u.setLastName("Policy");
    u.setEmail(name + "@policy.test");
    u.setPassword("not-a-login-hash");
    u.setCountry(country);
    u.setTeam(team);
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    if (role.equals("DIRECTEUR_COMMERCIAL")) {
      u.setDirectorCountry(country);
      u.setProjects(
        country.equals("BJ") ? Set.of("AZO", "ZEHAMI") : Set.of("AZO")
      );
    }
    if (role.equals("SUPERVISEUR")) {
      u.setZone("Zone " + country);
      u.setZoneLevel("COMMUNE");
    }
    return users.saveAndFlush(u);
  }

  Authentication auth(User u) {
    return UsernamePasswordAuthenticationToken.authenticated(
      u.getEmail(),
      "",
      u
        .getRoles()
        .stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
        .toList()
    );
  }

  void denied(int code, Runnable action) {
    assertEquals(
      code,
      assertThrows(ResponseStatusException.class, action::run)
        .getStatusCode()
        .value()
    );
  }

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    root = user("pol-root", "SUPER_ADMIN", "BJ", null);
    bj = user("pol-bj", "DIRECTEUR_COMMERCIAL", "BJ", null);
    ng = user("pol-ng", "DIRECTEUR_COMMERCIAL", "NG", null);
    sellerBj = user("pol-seller-bj", "COMMERCIAL", "BJ", "AZO");
    sellerNg = user("pol-seller-ng", "COMMERCIAL", "NG", "AZO");
    sellerZehami = user("pol-seller-zehami", "COMMERCIAL", "BJ", "ZEHAMI");
    supervisorBj = user("pol-supervisor-bj", "SUPERVISEUR", "BJ", "AZO");
    supervisorNg = user("pol-supervisor-ng", "SUPERVISEUR", "NG", "AZO");
  }

  CommercialRequest data(String email, String country, String project) {
    return new CommercialRequest(
      "Nouveau",
      "Compte",
      email,
      "strong-policy-password",
      project,
      null,
      country
    );
  }

  @Test
  void oneDirectorPerCountryWithMultipleProjects() {
    denied(409, () ->
      sales.create(
        data("duplicate@policy.test", "BJ", "AZO"),
        "DIRECTEUR_COMMERCIAL",
        null,
        Set.of("AZO", "ZEHAMI"),
        null,
        auth(root)
      )
    );
    var created = sales.create(
      data("togo@policy.test", "TG", "AZO"),
      "DIRECTEUR_COMMERCIAL",
      null,
      Set.of("AZO", "ZEHAMI"),
      null,
      auth(root)
    );
    assertEquals("TG", created.country());
    assertEquals(Set.of("AZO", "ZEHAMI"), created.projects());
    assertTrue(created.configured());
    denied(403, () ->
      sales.create(
        data("escalation@policy.test", "TG", "AZO"),
        "DIRECTEUR_COMMERCIAL",
        null,
        Set.of("AZO"),
        null,
        auth(bj)
      )
    );
  }

  @Test
  void countryAndProjectBoundariesProtectReadsAndWrites() {
    denied(403, () ->
      accounts.createAuthorized(
        data("outside-country@policy.test", "NG", "AZO"),
        auth(bj)
      )
    );
    denied(403, () ->
      accounts.createAuthorized(
        data("outside-project@policy.test", "NG", "ZEHAMI"),
        auth(ng)
      )
    );
    denied(403, () ->
      sales.assign(
        sellerNg.getId(),
        "COMMERCIAL",
        "AZO",
        supervisorBj.getId(),
        null,
        "BJ",
        null,
        null,
        auth(bj)
      )
    );
    assertFalse(
      sales
        .members(auth(bj))
        .stream()
        .anyMatch(m -> m.id().equals(sellerNg.getId()))
    );
    assertFalse(
      accounts
        .list(auth(ng))
        .stream()
        .anyMatch(m -> m.id().equals(sellerZehami.getId()))
    );
    assertNotNull(
      accounts.createAuthorized(
        data("root-nigeria@policy.test", "NG", "ZEHAMI"),
        auth(root)
      )
    );
  }

  @Test
  void geographicLevelAndSupervisorCountryAreEnforced() throws Exception {
    mvc
      .perform(
        post("/api/sales/members")
          .with(authentication(auth(bj)))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"account\":{\"firstName\":\"Zone\",\"lastName\":\"Test\",\"email\":\"missing-zone@policy.test\",\"password\":\"strong-policy-password\",\"team\":\"AZO\",\"country\":\"BJ\"},\"role\":\"SUPERVISEUR\",\"zone\":\"Atlantique\"}"
          )
      )
      .andExpect(status().isBadRequest());
    denied(400, () ->
      sales.assign(
        sellerBj.getId(),
        "COMMERCIAL",
        "AZO",
        supervisorNg.getId(),
        null,
        "BJ",
        null,
        null,
        auth(root)
      )
    );
    var assigned = sales.assign(
      sellerBj.getId(),
      "COMMERCIAL",
      "AZO",
      supervisorBj.getId(),
      null,
      "BJ",
      null,
      null,
      auth(bj)
    );
    assertEquals(supervisorBj.getId(), assigned.supervisorId());
    var department = sales.create(
      data("department@policy.test", "BJ", "ZEHAMI"),
      "SUPERVISEUR",
      "Atlantique",
      null,
      "DEPARTMENT",
      auth(bj)
    );
    assertEquals("DEPARTMENT", department.zoneLevel());
    assertEquals("Atlantique", department.zone());
  }

  Report report(User owner) {
    var r = new Report();
    r.setTitle(owner.getFirstName());
    r.setContent("Activité");
    r.setAuthor(owner);
    r.setTeam(owner.getTeam());
    r.setCountry(owner.getCountry());
    r.setStatus("SUBMITTED");
    return reports.saveAndFlush(r);
  }

  @Test
  void reportAndObjectiveReadsCannotLeakAcrossCountries() {
    var rBj = report(sellerBj);
    var rNg = report(sellerNg);
    var rZ = report(sellerZehami);
    assertNotNull(reportService.getReportById(rBj.getId(), auth(bj)));
    assertNotNull(reportService.getReportById(rZ.getId(), auth(bj)));
    denied(404, () -> reportService.getReportById(rNg.getId(), auth(bj)));
    denied(404, () -> reportService.getReportById(rZ.getId(), auth(ng)));
    assertNotNull(reportService.getReportById(rNg.getId(), auth(root)));
    assertFalse(
      sales
        .objectives(java.time.YearMonth.now(), auth(bj))
        .stream()
        .anyMatch(o -> o.commercialId().equals(sellerNg.getId()))
    );
  }

  @Test
  void announcementsAndNotificationsHaveTheSameAuthorizedAudience() {
    var bjNotice = comms.publish(
      "Bénin projets",
      "Coordination",
      "BJ",
      Set.of("AZO", "ZEHAMI"),
      auth(bj)
    );
    var ngNotice = comms.publish(
      "Nigeria",
      "Terrain",
      "NG",
      Set.of("AZO"),
      auth(ng)
    );
    var global = comms.publish(
      "Entreprise",
      "Information générale",
      null,
      Set.of(),
      auth(root)
    );
    var zehami = comms.publish(
      "Zehami",
      "Information projet",
      "BJ",
      Set.of("ZEHAMI"),
      auth(root)
    );
    var sellerFeed = comms.announcements(auth(sellerBj));
    assertTrue(sellerFeed.stream().anyMatch(a -> a.id.equals(bjNotice.id)));
    assertTrue(sellerFeed.stream().anyMatch(a -> a.id.equals(global.id)));
    assertFalse(
      sellerFeed
        .stream()
        .anyMatch(a -> a.id.equals(ngNotice.id) || a.id.equals(zehami.id))
    );
    assertFalse(
      comms
        .events(auth(sellerNg))
        .stream()
        .anyMatch(e -> e.message.contains("Bénin projets"))
    );
    assertTrue(
      comms
        .announcements(auth(bj))
        .stream()
        .anyMatch(a -> a.id.equals(zehami.id))
    );
    denied(403, () ->
      comms.publish("Interdit", "Non autorisé", null, Set.of(), auth(bj))
    );
    denied(403, () ->
      comms.publish(
        "Interdit",
        "Non autorisé",
        "NG",
        Set.of("ZEHAMI"),
        auth(ng)
      )
    );
    denied(403, () ->
      comms.publish(
        "Interdit",
        "Non autorisé",
        "BJ",
        Set.of("AZO"),
        auth(sellerBj)
      )
    );
  }

  @Test
  void databaseEnforcesTheDirectorSlotEvenOutsideTheService() {
    assertThrows(
      org.springframework.dao.DataIntegrityViolationException.class,
      () -> user("pol-duplicate-db", "DIRECTEUR_COMMERCIAL", "BJ", "AZO")
    );
  }

  @Test
  void unconfiguredLegacyDirectorCannotAccessCountryData() {
    bj.setDirectorCountry(null);
    users.saveAndFlush(bj);
    denied(403, () ->
      accounts.createAuthorized(
        data("legacy-blocked@policy.test", "BJ", "AZO"),
        auth(bj)
      )
    );
    assertFalse(
      sales
        .members(auth(bj))
        .stream()
        .anyMatch(m -> m.id().equals(sellerBj.getId()))
    );
  }
}
