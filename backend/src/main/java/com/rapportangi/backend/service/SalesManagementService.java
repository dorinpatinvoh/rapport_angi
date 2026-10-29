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
  private final SalesCountryRepository countries;
  private final CommunicationService comms;

  public SalesManagementService(
    UserRepository users,
    RoleRepository roles,
    ReportRepository reports,
    SupervisorSummaryRepository summaries,
    SalesAccess access,
    AccountService accounts,
    SalesCountryRepository countries,
    CommunicationService comms
  ) {
    this.users = users;
    this.roles = roles;
    this.reports = reports;
    this.summaries = summaries;
    this.access = access;
    this.accounts = accounts;
    this.countries = countries;
    this.comms = comms;
  }

  public record Member(
    Long id,
    String name,
    String email,
    String role,
    String team,
    Long supervisorId,
    String zone,
    boolean enabled,
    String country,
    Set<String> projects,
    String zoneLevel,
    boolean configured
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
      u.isEnabled(),
      u.getCountry(),
      new TreeSet<>(u.getProjects()),
      u.getZoneLevel(),
      !has(u, "DIRECTEUR_COMMERCIAL") || u.getDirectorCountry() != null
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
          manages(actor, u) ||
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
    return create(request, role, zone, null, "COMMUNE", auth);
  }

  public Member create(
    com.rapportangi.backend.dto.CommercialRequest request,
    String role,
    String zone,
    Set<String> projects,
    String zoneLevel,
    Authentication auth
  ) {
    var actor = access.current(auth);
    if (
      !director(actor) ||
      !(
        "SUPERVISEUR".equals(role) ||
        ("DIRECTEUR_COMMERCIAL".equals(role) && has(actor, "SUPER_ADMIN"))
      )
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Création de cette fonction non autorisée."
    );
    String country =
      request.country() == null ? actor.getCountry() : request.country();
    if (!scope(actor, country, request.team())) throw error(
      HttpStatus.FORBIDDEN,
      "Compte hors de votre pays ou de vos projets."
    );
    var normalized = new com.rapportangi.backend.dto.CommercialRequest(
      request.firstName(),
      request.lastName(),
      request.email(),
      request.password(),
      request.team(),
      null,
      country
    );
    var created = accounts.create(normalized, true);
    return assign(
      created.id(),
      role,
      request.team(),
      null,
      zone,
      country,
      projects,
      zoneLevel,
      auth
    );
  }

  public Member assign(
    Long id,
    String role,
    String team,
    Long supervisorId,
    String zone,
    Authentication auth
  ) {
    return assign(
      id,
      role,
      team,
      supervisorId,
      zone,
      null,
      null,
      "COMMUNE",
      auth
    );
  }

  public Member assign(
    Long id,
    String role,
    String team,
    Long supervisorId,
    String zone,
    String requestedCountry,
    Set<String> requestedProjects,
    String requestedLevel,
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
    String country =
      requestedCountry == null ? u.getCountry() : requestedCountry;
    String oldCountry = u.getCountry(),
      oldTeam = u.getTeam();
    if (!manages(actor, u) || !scope(actor, country, team)) throw error(
      HttpStatus.FORBIDDEN,
      "Affectation hors de votre pays ou de vos projets."
    );
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
    countries
      .locked(country)
      .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Pays inconnu."));
    Set<String> projects =
      requestedProjects == null
        ? u.getProjects().isEmpty()
          ? Set.of(team)
          : new HashSet<>(u.getProjects())
        : requestedProjects;
    if ("DIRECTEUR_COMMERCIAL".equals(role)) {
      if (
        projects.isEmpty() || !Set.of("AZO", "ZEHAMI").containsAll(projects)
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "Sélectionnez au moins un projet valide."
      );
      if (
        users
          .findAll()
          .stream()
          .anyMatch(
            other ->
              !other.getId().equals(id) &&
              country.equals(other.getDirectorCountry())
          )
      ) throw error(
        HttpStatus.CONFLICT,
        "Un directeur est déjà nommé pour ce pays. Réaffectez-le d'abord."
      );
    }
    String level = requestedLevel == null ? u.getZoneLevel() : requestedLevel;
    if (
      "SUPERVISEUR".equals(role) &&
      !List.of("DEPARTMENT", "COMMUNE").contains(level == null ? "" : level)
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "Précisez Département ou Commune pour la zone."
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
      (!"SUPERVISEUR".equals(role) ||
        !Objects.equals(team, u.getTeam()) ||
        !Objects.equals(country, u.getCountry()))
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
        !Objects.equals(country, supervisor.getCountry()) ||
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
        !Objects.equals(cleanZone, u.getZone()) ||
        !Objects.equals(level, u.getZoneLevel()) ||
        !Objects.equals(country, u.getCountry()));
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
    if (
      commercial(u) &&
      (!Objects.equals(country, u.getCountry()) ||
        !Objects.equals(team, u.getTeam())) &&
      reports
        .findByAuthorIdOrderByCreatedAtDesc(id)
        .stream()
        .anyMatch(r -> !"CONFIRMED".equals(r.getStatus()))
    ) throw error(
      HttpStatus.CONFLICT,
      "Terminez les rapports en cours avant de changer le pays ou le projet."
    );
    u.setCountry(country);
    u.setDirectorCountry("DIRECTEUR_COMMERCIAL".equals(role) ? country : null);
    u.setProjects("DIRECTEUR_COMMERCIAL".equals(role) ? projects : Set.of());
    u.setZoneLevel("SUPERVISEUR".equals(role) ? level : null);
    u.setRoles(Set.of(roles.findByName(role).orElseThrow()));
    u.setTeam(team);
    u.setSupervisor(supervisor);
    u.setZone("SUPERVISEUR".equals(role) ? cleanZone : null);
    u.setSecurityVersion(u.getSecurityVersion() + 1);
    var result = member(users.saveAndFlush(u));
    comms.record(
      actor,
      "ASSIGNMENT_CHANGED",
      "Affectation de " +
        result.name() +
        " : " +
        ("DIRECTEUR_COMMERCIAL".equals(role)
          ? "Directeur commercial"
          : "SUPERVISEUR".equals(role)
            ? "Superviseur"
            : "Commercial") +
        " · " +
        country +
        " · " +
        ("DIRECTEUR_COMMERCIAL".equals(role)
          ? String.join(", ", new TreeSet<>(projects))
          : team) +
        (cleanZone == null || cleanZone.isEmpty() ? "" : " · " + cleanZone),
      country,
      "DIRECTEUR_COMMERCIAL".equals(role) ? null : team
    );
    if (
      !Objects.equals(oldCountry, country) || !Objects.equals(oldTeam, team)
    ) comms.record(
      actor,
      "ASSIGNMENT_CHANGED",
      "Affectation d'un compte de cette équipe modifiée.",
      oldCountry,
      oldTeam
    );
    return result;
  }

  public List<SalesCountry> countries() {
    return countries.findAll(org.springframework.data.domain.Sort.by("name"));
  }

  public SalesCountry addCountry(
    String code,
    String name,
    Authentication auth
  ) {
    var actor = access.current(auth);
    if (!has(actor, "SUPER_ADMIN")) throw error(
      HttpStatus.FORBIDDEN,
      "Administration globale requise."
    );
    if (
      !java.util.Arrays.asList(java.util.Locale.getISOCountries()).contains(
        code
      )
    ) throw error(HttpStatus.BAD_REQUEST, "Code pays ISO invalide.");
    if (countries.existsById(code)) throw error(
      HttpStatus.CONFLICT,
      "Ce pays existe déjà."
    );
    var c = countries.saveAndFlush(new SalesCountry(code, name.strip()));
    comms.record(
      actor,
      "COUNTRY_CREATED",
      "Pays ajouté : " + c.name,
      null,
      null
    );
    return c;
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
          (director(actor) && scope(actor, u.getCountry(), u.getTeam())) ||
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
              Objects.equals(r.getCountry(), u.getCountry()) &&
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
          ((director(actor) && scope(actor, s.getCountry(), s.team)) ||
            s.supervisor.getId().equals(actor.getId()))
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
    String actorCountry = actor.getCountry();
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
    s.setCountry(actor.getCountry());
    s.reportDate = date;
    s.team = team;
    s.zone = actor.getZone();
    s.content = content.strip();
    s.reports.addAll(daily);
    return summary(summaries.saveAndFlush(s));
  }
}
