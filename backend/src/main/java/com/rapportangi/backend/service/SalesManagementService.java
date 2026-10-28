package com.rapportangi.backend.service;

import static com.rapportangi.backend.service.SalesAccess.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesManagementService {

  private final UserRepository users;
  private final RoleRepository roles;
  private final ReportRepository reports;
  private final SupervisorSummaryRepository summaries;
  private final SalesAccess access;
  private final AccountService accounts;

  public SalesManagementService(
    UserRepository users,
    RoleRepository roles,
    ReportRepository reports,
    SupervisorSummaryRepository summaries,
    SalesAccess access,
    AccountService accounts
  ) {
    this.users = users;
    this.roles = roles;
    this.reports = reports;
    this.summaries = summaries;
    this.access = access;
    this.accounts = accounts;
  }

  public record Member(
    Long id,
    String name,
    String email,
    String role,
    String team,
    Long supervisorId,
    String zone,
    boolean enabled
  ) {}

  private Member member(User u) {
    String role = has(u, "SUPER_ADMIN")
      ? "SUPER_ADMIN"
      : has(u, "DIRECTEUR_COMMERCIAL")
        ? "DIRECTEUR_COMMERCIAL"
        : has(u, "SUPERVISEUR")
          ? "SUPERVISEUR"
          : has(u, "ADMIN")
            ? "ADMIN"
            : "COMMERCIAL";
    return new Member(
      u.getId(),
      u.getFirstName() + " " + u.getLastName(),
      u.getEmail(),
      role,
      u.getTeam(),
      u.getSupervisor() == null ? null : u.getSupervisor().getId(),
      u.getSupervisor() == null ? u.getZone() : u.getSupervisor().getZone(),
      u.isEnabled()
    );
  }

  public List<Member> members(Authentication auth) {
    var actor = access.current(auth);
    if (!director(actor) && !has(actor, "SUPERVISEUR")) throw error(
      HttpStatus.FORBIDDEN,
      "Accès réservé à l'encadrement."
    );
    return users
      .findAll()
      .stream()
      .filter(
        u ->
          director(actor) ||
          u.getId().equals(actor.getId()) ||
          (u.getSupervisor() != null &&
            u.getSupervisor().getId().equals(actor.getId()))
      )
      .map(this::member)
      .toList();
  }

  public Member create(
    com.rapportangi.backend.dto.CommercialRequest request,
    String role,
    String zone,
    Authentication auth
  ) {
    var actor = access.current(auth);
    if (
      !director(actor) ||
      !(
        "SUPERVISEUR".equals(role) ||
        ("DIRECTEUR_COMMERCIAL".equals(role) && has(actor, "SUPER_ADMIN"))
      )
    ) {
      throw error(
        HttpStatus.FORBIDDEN,
        "Création de cette fonction non autorisée."
      );
    }
    // Creation and role assignment share this transaction: a failure leaves no partial account.
    var created = accounts.create(request, true);
    return assign(created.id(), role, request.team(), null, zone, auth);
  }

  public Member assign(
    Long id,
    String role,
    String team,
    Long supervisorId,
    String zone,
    Authentication auth
  ) {
    var actor = access.current(auth);
    if (!director(actor)) throw error(
      HttpStatus.FORBIDDEN,
      "Le directeur affecte les équipes et nomme les superviseurs."
    );
    var u = users
      .findLockedById(id)
      .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Compte introuvable"));
    if (
      u.getId().equals(actor.getId()) ||
      has(u, "SUPER_ADMIN") ||
      has(u, "ADMIN") ||
      (!has(actor, "SUPER_ADMIN") && has(u, "DIRECTEUR_COMMERCIAL"))
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Ce compte ne peut pas être modifié ici."
    );
    if (
      !List.of("COMMERCIAL", "SUPERVISEUR", "DIRECTEUR_COMMERCIAL").contains(
        role
      ) ||
      ("DIRECTEUR_COMMERCIAL".equals(role) && !has(actor, "SUPER_ADMIN"))
    ) throw error(HttpStatus.FORBIDDEN, "Nomination non autorisée.");
    if (!List.of("AZO", "ZEHAMI").contains(team)) throw error(
      HttpStatus.BAD_REQUEST,
      "Équipe invalide."
    );
    if (!u.isEnabled() && !"COMMERCIAL".equals(role)) throw error(
      HttpStatus.CONFLICT,
      "Activez d’abord ce compte dans Commerciaux avant de le nommer à une fonction d’encadrement."
    );
    var dependents = users
      .findAll()
      .stream()
      .filter(
        v -> v.getSupervisor() != null && v.getSupervisor().getId().equals(id)
      )
      .toList();
    String cleanZone = zone == null ? null : zone.strip();
    if (
      "SUPERVISEUR".equals(role) && (cleanZone == null || cleanZone.isBlank())
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "La zone du superviseur est obligatoire."
    );
    if (
      !dependents.isEmpty() &&
      (!"SUPERVISEUR".equals(role) || !Objects.equals(team, u.getTeam()))
    ) throw error(
      HttpStatus.CONFLICT,
      "Réaffectez d'abord les commerciaux de ce superviseur."
    );
    User supervisor = null;
    if ("COMMERCIAL".equals(role) && supervisorId != null) {
      supervisor = users
        .findLockedById(supervisorId)
        .orElseThrow(() ->
          error(HttpStatus.BAD_REQUEST, "Superviseur introuvable")
        );
      if (
        supervisor.getId().equals(id) ||
        !has(supervisor, "SUPERVISEUR") ||
        !supervisor.isEnabled() ||
        !Objects.equals(team, supervisor.getTeam()) ||
        supervisor.getZone() == null ||
        supervisor.getZone().isBlank()
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "Choisissez un superviseur actif de la même équipe avec une zone."
      );
    }
    if (
      commercial(u) &&
      !"COMMERCIAL".equals(role) &&
      reports
        .findByAuthorIdOrderByCreatedAtDesc(id)
        .stream()
        .anyMatch(r -> !"CONFIRMED".equals(r.getStatus()))
    ) throw error(
      HttpStatus.CONFLICT,
      "Ce commercial doit d'abord terminer ses rapports en cours avant de changer de fonction."
    );
    boolean changesSupervisor =
      has(u, "SUPERVISEUR") &&
      (!"SUPERVISEUR".equals(role) ||
        !Objects.equals(team, u.getTeam()) ||
        !Objects.equals(cleanZone, u.getZone()));
    if (changesSupervisor) {
      var included = summaries
        .findAll()
        .stream()
        .flatMap(s -> s.reports.stream())
        .map(Report::getId)
        .collect(java.util.stream.Collectors.toSet());
      if (
        reports
          .findAll()
          .stream()
          .anyMatch(
            r ->
              r.getSupervisor() != null &&
              r.getSupervisor().getId().equals(id) &&
              !"DRAFT".equals(r.getStatus()) &&
              !included.contains(r.getId())
          )
      ) throw error(
        HttpStatus.CONFLICT,
        "Transmettez d'abord les synthèses des rapports de ce superviseur avant de changer sa fonction, son équipe ou sa zone."
      );
    }
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    u.setTeam(team);
    u.setSupervisor(supervisor);
    u.setZone("SUPERVISEUR".equals(role) ? cleanZone : null);
    u.setSecurityVersion(u.getSecurityVersion() + 1);
    return member(users.saveAndFlush(u));
  }

  public record Progress(
    String code,
    String label,
    int target,
    long achieved,
    long remaining,
    int percent
  ) {}

  public record Monthly(
    Long commercialId,
    String name,
    String team,
    String zone,
    List<Progress> goals,
    long pendingReports,
    long unquantifiedReports
  ) {}

  public List<Monthly> objectives(YearMonth month, Authentication auth) {
    var actor = access.current(auth);
    var all = reports.findAll();
    return users
      .findAll()
      .stream()
      .filter(SalesAccess::commercial)
      .filter(
        u ->
          u.getId().equals(actor.getId()) ||
          director(actor) ||
          (has(actor, "SUPERVISEUR") &&
            u.getSupervisor() != null &&
            u.getSupervisor().getId().equals(actor.getId()))
      )
      .map(u -> {
        var own = all
          .stream()
          .filter(
            r ->
              r.getAuthor().getId().equals(u.getId()) &&
              Objects.equals(r.getTeam(), u.getTeam()) &&
              YearMonth.from(r.getReportDate()).equals(month)
          )
          .toList();
        var confirmed = own
          .stream()
          .filter(r -> "CONFIRMED".equals(r.getStatus()))
          .toList();
        var goals = SalesGoals.definitions(u.getTeam())
          .stream()
          .map(d -> {
            long achieved = confirmed
              .stream()
              .mapToLong(r -> r.getGoalCounts().getOrDefault(d.code(), 0))
              .sum();
            return new Progress(
              d.code(),
              d.label(),
              d.target(),
              achieved,
              Math.max(0, d.target() - achieved),
              (int) Math.min(100, (achieved * 100) / d.target())
            );
          })
          .toList();
        return new Monthly(
          u.getId(),
          u.getFirstName() + " " + u.getLastName(),
          u.getTeam(),
          u.getSupervisor() == null ? null : u.getSupervisor().getZone(),
          goals,
          own
            .stream()
            .filter(r ->
              List.of("SUBMITTED", "RETURNED").contains(r.getStatus())
            )
            .count(),
          confirmed
            .stream()
            .filter(r ->
              SalesGoals.definitions(u.getTeam())
                .stream()
                .anyMatch(d -> !r.getGoalCounts().containsKey(d.code()))
            )
            .count()
        );
      })
      .toList();
  }

  public record Summary(
    Long id,
    Long supervisorId,
    String supervisorName,
    LocalDate reportDate,
    String team,
    String zone,
    String content,
    LocalDateTime createdAt,
    List<Long> reportIds,
    int commercialCount,
    Map<String, Long> totals
  ) {}

  private Summary summary(SupervisorSummary s) {
    var totals = new TreeMap<String, Long>();
    s.reports.forEach(r ->
      r
        .getGoalCounts()
        .forEach((k, v) -> totals.merge(k, v.longValue(), Long::sum))
    );
    return new Summary(
      s.id,
      s.supervisor.getId(),
      s.supervisor.getFirstName() + " " + s.supervisor.getLastName(),
      s.reportDate,
      s.team,
      s.zone,
      s.content,
      s.createdAt,
      s.reports.stream().map(Report::getId).sorted().toList(),
      (int) s.reports
        .stream()
        .map(r -> r.getAuthor().getId())
        .distinct()
        .count(),
      totals
    );
  }

  public List<Summary> summaries(LocalDate date, Authentication auth) {
    var actor = access.current(auth);
    if (!director(actor) && !has(actor, "SUPERVISEUR")) throw error(
      HttpStatus.FORBIDDEN,
      "Accès réservé à l'encadrement."
    );
    return summaries
      .findAll(
        org.springframework.data.domain.Sort.by("createdAt").descending()
      )
      .stream()
      .filter(
        s ->
          s.reportDate.equals(date) &&
          (director(actor) || s.supervisor.getId().equals(actor.getId()))
      )
      .map(this::summary)
      .toList();
  }

  public Summary sendSummary(
    LocalDate date,
    String content,
    Authentication auth
  ) {
    var actor = access.current(auth);
    if (!has(actor, "SUPERVISEUR")) throw error(
      HttpStatus.FORBIDDEN,
      "Seul un superviseur envoie la synthèse."
    );
    actor = users.findLockedById(actor.getId()).orElseThrow();
    if (date.isAfter(LocalDate.now(ZoneId.of("Africa/Lagos")))) throw error(
      HttpStatus.BAD_REQUEST,
      "La journée ne peut pas être dans le futur."
    );
    if (
      actor.getTeam() == null ||
      actor.getZone() == null ||
      actor.getZone().isBlank()
    ) throw error(
      HttpStatus.CONFLICT,
      "Votre équipe et votre zone doivent être affectées."
    );
    if (
      summaries.existsBySupervisorIdAndReportDateAndTeam(
        actor.getId(),
        date,
        actor.getTeam()
      )
    ) throw error(
      HttpStatus.CONFLICT,
      "Une synthèse a déjà été transmise pour cette journée et cette équipe."
    );
    Long actorId = actor.getId();
    String team = actor.getTeam();
    var daily = reports
      .findAll()
      .stream()
      .filter(
        r ->
          r.getSupervisor() != null &&
          r.getSupervisor().getId().equals(actorId) &&
          r.getReportDate().equals(date) &&
          Objects.equals(r.getTeam(), team) &&
          !"DRAFT".equals(r.getStatus())
      )
      .toList();
    if (
      daily.stream().anyMatch(r -> !"CONFIRMED".equals(r.getStatus()))
    ) throw error(
      HttpStatus.CONFLICT,
      "Traitez les rapports soumis ou renvoyés pour correction avant la synthèse."
    );
    if (daily.isEmpty()) throw error(
      HttpStatus.CONFLICT,
      "Aucun rapport validé à synthétiser pour cette journée."
    );
    var s = new SupervisorSummary();
    s.supervisor = actor;
    s.reportDate = date;
    s.team = team;
    s.zone = actor.getZone();
    s.content = content.strip();
    s.reports.addAll(daily);
    return summary(summaries.saveAndFlush(s));
  }
}
