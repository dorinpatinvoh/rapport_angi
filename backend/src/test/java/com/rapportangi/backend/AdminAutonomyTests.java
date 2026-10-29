package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import com.rapportangi.backend.service.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:autonomy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
)
@Transactional
class AdminAutonomyTests {

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  ReportRepository reports;

  @Autowired
  SupervisorSummaryRepository summaries;

  @Autowired
  ActivityEventRepository events;

  @Autowired
  ZoneGoalsService zones;

  @Autowired
  SalesManagementService sales;

  @Autowired
  ReportService reportService;

  @Autowired
  ReportPdfService pdf;

  @Autowired
  PlatformTransactionManager transactionManager;

  record Fixture(
    User root,
    User director,
    User supervisor,
    User reserve,
    User seller,
    User candidate
  ) {}

  User user(String name, String role, String country) {
    var u = new User();
    u.setFirstName(name);
    u.setLastName("Autonomy");
    u.setEmail(name + "@autonomy.test");
    u.setPassword("not-for-login");
    u.setCountry(country);
    u.setTeam("AZO");
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    if (role.equals("SUPERVISEUR")) {
      u.setZone(name.equals("supervisor") ? "Littoral" : "Atlantique");
      u.setZoneLevel("DEPARTMENT");
    }
    if (role.equals("DIRECTEUR_COMMERCIAL")) {
      u.setDirectorCountry(country);
      u.setProjects(Set.of("AZO", "ZEHAMI"));
    }
    return users.saveAndFlush(u);
  }

  Fixture fixture() {
    var root = user("root", "SUPER_ADMIN", "BJ");
    var director = user("director", "DIRECTEUR_COMMERCIAL", "BJ");
    var supervisor = user("supervisor", "SUPERVISEUR", "BJ");
    var reserve = user("reserve", "SUPERVISEUR", "BJ");
    var seller = user("seller", "COMMERCIAL", "BJ");
    seller.setSupervisor(supervisor);
    users.saveAndFlush(seller);
    return new Fixture(
      root,
      director,
      supervisor,
      reserve,
      seller,
      user("candidate", "COMMERCIAL", "BJ")
    );
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

  void denied(int status, Runnable r) {
    assertEquals(
      status,
      assertThrows(ResponseStatusException.class, r::run)
        .getStatusCode()
        .value()
    );
  }

  List<ZoneGoalsService.Metric> metrics(int target) {
    return List.of(
      new ZoneGoalsService.Metric(
        "VISITES_BOUTIQUES",
        "Visites de boutiques",
        target
      )
    );
  }

  ZoneGoalsService.Input input(
    String name,
    String level,
    Long parent,
    boolean inherit,
    int target,
    Long version
  ) {
    return new ZoneGoalsService.Input(
      "BJ",
      "AZO",
      level,
      name,
      parent,
      5,
      inherit,
      inherit ? List.of() : metrics(target),
      version
    );
  }

  SalesManagementService.Monthly monthly(User user) {
    return sales
      .objectives(YearMonth.now(ZoneId.of("Africa/Lagos")), auth(user))
      .stream()
      .filter(m -> m.commercialId().equals(user.getId()))
      .findFirst()
      .orElseThrow();
  }

  @Test
  void zoneTargetsApplyImmediatelyAndUpdateWithoutChangingAssignments() {
    var f = fixture();
    var grid = zones.save(
      null,
      input("Littoral", "DEPARTMENT", null, false, 12, null),
      auth(f.director)
    );
    assertEquals(grid.id(), f.supervisor.getZoneConfigId());
    assertEquals(12, monthly(f.seller).goals().get(0).target());
    var changed = zones.save(
      grid.id(),
      input("Littoral", "DEPARTMENT", null, false, 20, grid.version()),
      auth(f.director)
    );
    assertEquals(20, monthly(f.seller).goals().get(0).target());
    assertEquals(20, monthly(f.seller).goals().get(0).remaining());
    assertEquals(1, changed.assignedStaff());
    assertEquals(0, f.seller.getSecurityVersion());
    denied(409, () ->
      zones.save(
        grid.id(),
        input("Littoral", "DEPARTMENT", null, false, 30, grid.version()),
        auth(f.director)
      )
    );
  }

  @Test
  void communeCanInheritOrOverrideItsDepartment() {
    var f = fixture();
    var department = zones.save(
      null,
      input("Littoral", "DEPARTMENT", null, false, 12, null),
      auth(f.root)
    );
    var commune = zones.save(
      null,
      input("Cotonou", "COMMUNE", department.id(), true, 0, null),
      auth(f.director)
    );
    sales.assign(
      f.supervisor.getId(),
      "SUPERVISEUR",
      "AZO",
      null,
      "ignored",
      "BJ",
      null,
      "COMMUNE",
      commune.id(),
      auth(f.director)
    );
    assertEquals(12, monthly(f.seller).goals().get(0).target());
    assertTrue(monthly(f.seller).gridSource().contains("Littoral"));
    zones.save(
      department.id(),
      input("Littoral", "DEPARTMENT", null, false, 18, department.version()),
      auth(f.root)
    );
    assertEquals(18, monthly(f.seller).goals().get(0).target());
    zones.save(
      commune.id(),
      input("Cotonou", "COMMUNE", department.id(), false, 7, commune.version()),
      auth(f.director)
    );
    assertEquals(7, monthly(f.seller).goals().get(0).target());
    assertEquals(0, f.supervisor.getSecurityVersion());
  }

  @Test
  void savedReportAndPdfRetainCustomLabelsAndRealizations() throws Exception {
    var f = fixture();
    var grid = zones.save(
      null,
      new ZoneGoalsService.Input(
        "BJ",
        "AZO",
        "DEPARTMENT",
        "Littoral",
        null,
        5,
        false,
        List.of(
          new ZoneGoalsService.Metric(
            "VISITES_BOUTIQUES",
            "Visites de boutiques",
            12
          ),
          new ZoneGoalsService.Metric(
            "CLIENTS",
            "Clients qualifiés de la zone",
            200
          )
        ),
        null
      ),
      auth(f.director)
    );
    var request = new ReportRequestDto(
      "Terrain",
      "Journée de prospection",
      "SUBMITTED",
      null
    );
    request.setGoalCounts(Map.of("VISITES_BOUTIQUES", 4, "CLIENTS", 2));
    var report = reportService.createReport(request, auth(f.seller));
    reportService.review(
      report.getId(),
      "CONFIRMED",
      "",
      report.getVersion(),
      auth(f.supervisor)
    );
    var updated = new ZoneGoalsService.Input(
      "BJ",
      "AZO",
      "DEPARTMENT",
      "Littoral",
      null,
      6,
      false,
      List.of(
        new ZoneGoalsService.Metric(
          "VISITES_BOUTIQUES",
          "Visites qualifiées",
          8
        )
      ),
      grid.version()
    );
    zones.save(grid.id(), updated, auth(f.director));
    var saved = reportService.getReportById(report.getId(), auth(f.root));
    assertEquals(4, saved.getGoalCounts().get("VISITES_BOUTIQUES"));
    assertEquals(
      "Visites de boutiques",
      saved.getGoalLabels().get("VISITES_BOUTIQUES")
    );
    assertEquals(
      "Clients qualifiés de la zone",
      saved.getGoalLabels().get("CLIENTS")
    );
    assertEquals(4, monthly(f.seller).goals().get(0).remaining());
    try (var document = Loader.loadPDF(pdf.render(saved))) {
      assertTrue(
        new PDFTextStripper()
          .getText(document)
          .contains("Clients qualifiés de la zone : 2")
      );
      assertTrue(
        new PDFTextStripper()
          .getText(document)
          .contains("Visites de boutiques : 4")
      );
    }
  }

  @Test
  void scopeAndInvalidConfigurationsAreRejected() {
    var f = fixture();
    denied(403, () ->
      zones.save(
        null,
        input("Littoral", "DEPARTMENT", null, false, 12, null),
        auth(f.seller)
      )
    );
    var foreign = new ZoneGoalsService.Input(
      "NG",
      "AZO",
      "DEPARTMENT",
      "Lagos",
      null,
      3,
      false,
      metrics(4),
      null
    );
    denied(403, () -> zones.save(null, foreign, auth(f.director)));
    denied(400, () ->
      zones.save(
        null,
        input("Cotonou", "COMMUNE", null, true, 0, null),
        auth(f.root)
      )
    );
    var zero = zones.save(
      null,
      input("Littoral", "DEPARTMENT", null, false, 0, null),
      auth(f.director)
    );
    assertEquals(100, monthly(f.seller).goals().get(0).percent());
  }

  @Test
  void deactivationIsReversibleAndProtectsSupervisorDependencies() {
    var f = fixture();
    denied(409, () ->
      sales.setStatus(f.supervisor.getId(), false, 0, auth(f.director))
    );
    var inactive = sales.setStatus(
      f.seller.getId(),
      false,
      0,
      auth(f.director)
    );
    assertFalse(inactive.enabled());
    assertEquals(1, inactive.revision());
    denied(409, () ->
      sales.setStatus(f.seller.getId(), true, 0, auth(f.director))
    );
    assertTrue(
      sales
        .setStatus(f.seller.getId(), true, inactive.revision(), auth(f.root))
        .enabled()
    );
    denied(403, () ->
      sales.setStatus(f.root.getId(), false, 0, auth(f.director))
    );
    assertFalse(
      sales.setStatus(f.reserve.getId(), false, 0, auth(f.director)).enabled()
    );
  }

  @Test
  void supervisorCannotBeDisabledBeforeItsConfirmedReportsAreSummarized() {
    var f = fixture();
    var r = new Report();
    r.setTitle("Validation en attente de synthèse");
    r.setContent("Activité");
    r.setAuthor(f.seller);
    r.setSupervisor(f.reserve);
    r.setTeam("AZO");
    r.setStatus("CONFIRMED");
    r = reports.saveAndFlush(r);
    denied(409, () ->
      sales.setStatus(f.reserve.getId(), false, 0, auth(f.director))
    );
    var summary = new SupervisorSummary();
    summary.supervisor = f.reserve;
    summary.reportDate = r.getReportDate();
    summary.team = "AZO";
    summary.zone = f.reserve.getZone();
    summary.content = "Synthèse terminée";
    summary.reports.add(r);
    summaries.saveAndFlush(summary);
    assertFalse(
      sales.setStatus(f.reserve.getId(), false, 0, auth(f.director)).enabled()
    );
  }

  @Test
  void countryDirectorReplacementIsRootOnlyAndKeepsTheUniqueSlot() {
    var f = fixture();
    denied(403, () ->
      sales.replaceDirector(
        "BJ",
        f.director.getId(),
        f.candidate.getId(),
        auth(f.director)
      )
    );
    var result = sales.replaceDirector(
      "BJ",
      f.director.getId(),
      f.candidate.getId(),
      auth(f.root)
    );
    assertEquals(f.candidate.getId(), result.id());
    assertEquals(Set.of("AZO", "ZEHAMI"), result.projects());
    assertFalse(f.director.isEnabled());
    assertNull(f.director.getDirectorCountry());
    assertEquals(
      1,
      users
        .findAll()
        .stream()
        .filter(u -> "BJ".equals(u.getDirectorCountry()))
        .count()
    );
    denied(409, () ->
      sales.replaceDirector(
        "BJ",
        f.director.getId(),
        f.director.getId(),
        auth(f.root)
      )
    );
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedReplacementRollsBackTheOldDirectorAndAudit() {
    var tx = new TransactionTemplate(transactionManager);
    var f = tx.execute(status -> fixture());
    try {
      denied(409, () ->
        tx.execute(status ->
          sales.replaceDirector(
            "BJ",
            f.director.getId(),
            f.supervisor.getId(),
            auth(f.root)
          )
        )
      );
      tx.executeWithoutResult(status -> {
        var previous = users.findById(f.director.getId()).orElseThrow();
        assertTrue(previous.isEnabled());
        assertEquals("BJ", previous.getDirectorCountry());
        assertEquals(0, events.count());
      });
    } finally {
      tx.executeWithoutResult(status -> {
        events.deleteAll();
        users.findAll().forEach(u -> u.setSupervisor(null));
        users.flush();
        users.deleteAll();
      });
    }
  }
}
