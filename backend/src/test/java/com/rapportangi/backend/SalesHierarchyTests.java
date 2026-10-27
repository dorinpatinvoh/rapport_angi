package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rapportangi.backend.dto.ReportRequestDto;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import com.rapportangi.backend.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class SalesHierarchyTests {

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  SalesManagementService sales;

  @Autowired
  ReportService service;

  @Autowired
  PasswordResetService reset;

  @Autowired
  ReportPdfService pdf;

  @Autowired
  PasswordEncoder passwords;

  @Autowired
  WebApplicationContext context;

  User director, supervisor, otherSupervisor, seller, zehami, superadmin;
  MockMvc mvc;
  LocalDate today = LocalDate.now(ZoneId.of("Africa/Lagos"));

  User account(String name, String role, String team) {
    var u = new User();
    u.setFirstName(name);
    u.setLastName("Test");
    u.setEmail(name + "@sales.test");
    u.setPassword(passwords.encode("sales-test-password"));
    u.setTeam(team);
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    if (role.equals("SUPERVISEUR")) u.setZone(name + " zone");
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

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
    director = account("director", "DIRECTEUR_COMMERCIAL", null);
    superadmin = account("root", "SUPER_ADMIN", null);
    supervisor = account("supervisor", "SUPERVISEUR", "AZO");
    otherSupervisor = account("other-supervisor", "SUPERVISEUR", "AZO");
    seller = account("seller", "COMMERCIAL", "AZO");
    seller.setSupervisor(supervisor);
    users.saveAndFlush(seller);
    zehami = account("zehami", "COMMERCIAL", "ZEHAMI");
  }

  ReportRequestDto request(
    String status,
    LocalDate day,
    Map<String, Integer> goals
  ) {
    var r = new ReportRequestDto("Prospection", "Compte rendu", status, null);
    r.setMode("STATS");
    r.setReportDate(day);
    r.setGoalCounts(goals);
    return r;
  }

  void denied(int status, Runnable action) {
    assertEquals(
      status,
      assertThrows(ResponseStatusException.class, action::run)
        .getStatusCode()
        .value()
    );
  }

  @Test
  void directorNominatesAndAssignsWithoutEscalatingPrivileges()
    throws Exception {
    denied(403, () ->
      sales.assign(
        seller.getId(),
        "SUPERVISEUR",
        "AZO",
        null,
        "Nord",
        auth(supervisor)
      )
    );
    denied(403, () ->
      sales.assign(
        seller.getId(),
        "DIRECTEUR_COMMERCIAL",
        "AZO",
        null,
        null,
        auth(director)
      )
    );
    denied(400, () ->
      sales.assign(
        seller.getId(),
        "COMMERCIAL",
        "ZEHAMI",
        supervisor.getId(),
        null,
        auth(director)
      )
    );
    denied(400, () ->
      sales.assign(
        seller.getId(),
        "SUPERVISEUR",
        "AZO",
        null,
        " ",
        auth(director)
      )
    );
    seller.setEnabled(false);
    denied(409, () ->
      sales.assign(
        seller.getId(),
        "SUPERVISEUR",
        "AZO",
        null,
        "Nord",
        auth(director)
      )
    );
    seller.setEnabled(true);
    sales.assign(
      seller.getId(),
      "SUPERVISEUR",
      "AZO",
      null,
      "Cotonou Nord",
      auth(director)
    );
    assertTrue(SalesAccess.has(seller, "SUPERVISEUR"));
    assertEquals(1, seller.getSecurityVersion());
    sales.assign(
      zehami.getId(),
      "COMMERCIAL",
      "AZO",
      seller.getId(),
      null,
      auth(director)
    );
    assertEquals(seller.getId(), zehami.getSupervisor().getId());
    denied(409, () ->
      sales.assign(
        seller.getId(),
        "COMMERCIAL",
        "AZO",
        null,
        null,
        auth(director)
      )
    );
    denied(409, () ->
      sales.assign(
        seller.getId(),
        "SUPERVISEUR",
        "ZEHAMI",
        null,
        "Sud",
        auth(director)
      )
    );
    assertEquals(2, sales.members(auth(seller)).size()); // self + assigned commercial
    denied(403, () -> sales.members(auth(zehami)));
    mvc
      .perform(
        put("/api/sales/members/" + director.getId())
          .with(user(supervisor.getEmail()).roles("SUPERVISEUR"))
          .with(csrf())
          .contentType("application/json")
          .content("{\"role\":\"COMMERCIAL\",\"team\":\"AZO\"}")
      )
      .andExpect(status().isForbidden());
    denied(404, () ->
      reset.resetCommercial(director.getId(), "not-a-real-password")
    );
  }

  @Test
  void reportRoutingIsScopedAndDirectorCannotChangeContentOrReview()
    throws Exception {
    var draft = service.createReport(
      request("DRAFT", today, Map.of()),
      auth(seller)
    );
    denied(404, () -> service.getReportById(draft.getId(), auth(supervisor)));
    denied(404, () -> service.getReportById(draft.getId(), auth(director)));
    var req = request("SUBMITTED", today, Map.of("CLIENTS", 12));
    req.setVersion(draft.getVersion());
    var r = service.updateReport(draft.getId(), req, auth(seller));
    assertEquals(supervisor.getId(), r.getSupervisorId());
    assertEquals(supervisor.getZone(), r.getZone());
    assertTrue(
      service
        .getAllReports(auth(supervisor))
        .stream()
        .anyMatch(x -> x.getId().equals(r.getId()))
    );
    denied(404, () -> service.getReportById(r.getId(), auth(otherSupervisor)));
    denied(403, () -> service.updateReport(r.getId(), req, auth(director)));
    denied(403, () ->
      service.review(r.getId(), "CONFIRMED", "", r.getVersion(), auth(director))
    );
    denied(403, () ->
      service.review(
        r.getId(),
        "CONFIRMED",
        "",
        r.getVersion(),
        auth(superadmin)
      )
    );
    denied(403, () -> service.createReport(req, auth(director)));
    denied(409, () -> service.deleteReport(r.getId(), auth(director))); // non-draft conflicts first
  }

  @Test
  void correctionRequiresLocationAndCommentAndHistoryPersists()
    throws Exception {
    var r = service.createReport(
      request("SUBMITTED", today, Map.of("CLIENTS", 12)),
      auth(seller)
    );
    String endpoint = "/api/reports/" + r.getId() + "/review";
    mvc
      .perform(
        post(endpoint)
          .with(user(supervisor.getEmail()).roles("SUPERVISEUR"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"action\":\"RETURNED\",\"comment\":\"Détail attendu\",\"version\":" +
              r.getVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post(endpoint)
          .with(user(supervisor.getEmail()).roles("SUPERVISEUR"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"action\":\"RETURNED\",\"location\":\"Clients\",\"version\":" +
              r.getVersion() +
              "}"
          )
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post(endpoint)
          .with(user(supervisor.getEmail()).roles("SUPERVISEUR"))
          .with(csrf())
          .contentType("application/json")
          .content(
            "{\"action\":\"RETURNED\",\"location\":\"Réalisations du jour : Clients\",\"comment\":\"Retirez les doublons\",\"version\":" +
              r.getVersion() +
              "}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("RETURNED"));
    var returned = service.getReportById(r.getId(), auth(seller));
    assertTrue(
      returned
        .getReviews()
        .get(0)
        .comment()
        .contains("Réalisations du jour : Clients")
    );
    var req = request("SUBMITTED", today, Map.of("CLIENTS", 10));
    req.setVersion(returned.getVersion());
    var updated = service.updateReport(r.getId(), req, auth(seller));
    var confirmed = service.review(
      r.getId(),
      "CONFIRMED",
      "Vérifié",
      updated.getVersion(),
      auth(supervisor)
    );
    assertEquals(2, confirmed.getReviews().size());
    assertEquals(10, confirmed.getGoalCounts().get("CLIENTS"));
    try (
      var document = org.apache.pdfbox.Loader.loadPDF(pdf.render(confirmed))
    ) {
      var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertTrue(text.contains("Clients : 10"));
      assertTrue(text.contains("Zone : supervisor zone"));
      assertTrue(text.contains("Retirez les doublons"));
    }
  }

  @Test
  void monthlyTargetsCountOnlyConfirmedReportsInTheRequestedMonth() {
    var previous = service.createReport(
      request(
        "SUBMITTED",
        today.withDayOfMonth(1).minusDays(1),
        Map.of("CLIENTS", 99)
      ),
      auth(seller)
    );
    service.review(
      previous.getId(),
      "CONFIRMED",
      "",
      previous.getVersion(),
      auth(supervisor)
    );
    var r = service.createReport(
      request(
        "SUBMITTED",
        today,
        Map.of("CLIENTS", 210, "AGENCES_FLOTTE", 3, "SPEEDERS_INDEPENDANTS", 8)
      ),
      auth(seller)
    );
    var row = sales.objectives(YearMonth.from(today), auth(seller)).get(0);
    assertEquals(0, row.goals().get(0).achieved());
    assertEquals(1, row.pendingReports());
    service.review(
      r.getId(),
      "CONFIRMED",
      "",
      r.getVersion(),
      auth(supervisor)
    );
    row = sales.objectives(YearMonth.from(today), auth(seller)).get(0);
    assertEquals(7, row.goals().get(0).remaining());
    assertEquals(37, row.goals().get(1).remaining());
    assertEquals(210, row.goals().get(2).achieved());
    assertEquals(0, row.goals().get(2).remaining());
    assertEquals(100, row.goals().get(2).percent());
    assertEquals(
      1,
      sales.objectives(YearMonth.from(today), auth(supervisor)).size()
    );
    var z = sales.objectives(YearMonth.from(today), auth(zehami)).get(0);
    assertEquals(
      List.of(25, 200),
      z.goals().stream().map(SalesManagementService.Progress::target).toList()
    );
    denied(400, () ->
      service.createReport(
        request("DRAFT", today, Map.of("PROFESSIONNELS", 2)),
        auth(seller)
      )
    );
    denied(400, () ->
      service.createReport(
        request("DRAFT", today, Map.of("CLIENTS", -1)),
        auth(seller)
      )
    );
  }

  @Test
  void summariesIncludeOnlyOwnValidatedReportsAndFreezeSources() {
    var r = service.createReport(
      request("SUBMITTED", today, Map.of("CLIENTS", 10)),
      auth(seller)
    );
    denied(409, () -> sales.sendSummary(today, "Résumé", auth(supervisor)));
    denied(403, () -> sales.sendSummary(today, "Résumé", auth(director)));
    service.review(
      r.getId(),
      "CONFIRMED",
      "",
      r.getVersion(),
      auth(supervisor)
    );
    var s = sales.sendSummary(
      today,
      "Zone visitée, 10 clients inscrits.",
      auth(supervisor)
    );
    assertEquals(List.of(r.getId()), s.reportIds());
    assertEquals(10L, s.totals().get("CLIENTS"));
    assertTrue(
      sales
        .summaries(today, auth(director))
        .stream()
        .anyMatch(x -> x.id().equals(s.id()))
    );
    assertTrue(sales.summaries(today, auth(otherSupervisor)).isEmpty());
    denied(403, () -> sales.summaries(today, auth(seller)));
    denied(409, () -> sales.sendSummary(today, "Doublon", auth(supervisor)));
    var late = account("late", "COMMERCIAL", "AZO");
    late.setSupervisor(supervisor);
    users.saveAndFlush(late);
    denied(409, () ->
      service.createReport(
        request("SUBMITTED", today, Map.of("CLIENTS", 1)),
        auth(late)
      )
    );
    sales.assign(
      seller.getId(),
      "COMMERCIAL",
      "AZO",
      otherSupervisor.getId(),
      null,
      auth(director)
    );
    assertEquals(
      supervisor.getId(),
      service.getReportById(r.getId(), auth(director)).getSupervisorId()
    );
    assertEquals(
      s.zone(),
      sales
        .summaries(today, auth(director))
        .stream()
        .filter(x -> x.id().equals(s.id()))
        .findFirst()
        .orElseThrow()
        .zone()
    );
  }

  @Test
  void unassignedCommercialCannotSubmitAndDuplicateDayCannotBeCountedTwice() {
    denied(409, () ->
      service.createReport(
        request("SUBMITTED", today, Map.of("PROFESSIONNELS", 1)),
        auth(zehami)
      )
    );
    service.createReport(
      request("SUBMITTED", today, Map.of("CLIENTS", 1)),
      auth(seller)
    );
    denied(409, () ->
      service.createReport(
        request("SUBMITTED", today, Map.of("CLIENTS", 2)),
        auth(seller)
      )
    );
  }
}
