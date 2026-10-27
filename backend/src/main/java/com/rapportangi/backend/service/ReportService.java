package com.rapportangi.backend.service;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ReportService {

  private final ReportRepository reports;
  private final UserRepository users;
  private final MobileStatsService mobile;
  private final SupervisorSummaryRepository summaries;

  public ReportService(
    ReportRepository reports,
    UserRepository users,
    MobileStatsService mobile,
    SupervisorSummaryRepository summaries
  ) {
    this.reports = reports;
    this.users = users;
    this.mobile = mobile;
    this.summaries = summaries;
  }

  public List<ReportResponseDto> getAllReports(Authentication auth) {
    var user = currentUser(auth);
    return reports
      .findAll(
        org.springframework.data.domain.Sort.by("createdAt").descending()
      )
      .stream()
      .filter(r -> SalesAccess.reads(user, r))
      .map(this::map)
      .toList();
  }

  public ReportResponseDto getReportById(Long id, Authentication auth) {
    return map(accessible(id, auth));
  }

  @Transactional
  public ReportResponseDto createReport(
    ReportRequestDto request,
    Authentication auth
  ) {
    var user = currentUser(auth);
    if (
      !SalesAccess.commercial(user) &&
      !SalesAccess.has(user, "SUPER_ADMIN") &&
      !SalesAccess.has(user, "ADMIN")
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Seuls les commerciaux rédigent les rapports terrain."
    );
    var report = new Report();
    report.setAuthor(user);
    String team =
      user.getTeam() != null
        ? user.getTeam()
        : isAdmin(auth)
          ? request.getTeam()
          : null;
    if (team == null) throw error(
      HttpStatus.CONFLICT,
      "Votre équipe doit être renseignée par le super administrateur."
    );
    report.setTeam(team);
    apply(report, request, user);
    return map(reports.saveAndFlush(report));
  }

  @Transactional
  public ReportResponseDto updateReport(
    Long id,
    ReportRequestDto request,
    Authentication auth
  ) {
    var report = accessible(id, auth);
    var user = currentUser(auth);
    if (
      SalesAccess.has(user, "DIRECTEUR_COMMERCIAL") ||
      SalesAccess.has(user, "SUPERVISEUR") ||
      !report.getAuthor().getId().equals(user.getId())
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Seul l'auteur peut modifier le contenu."
    );
    if (!List.of("DRAFT", "RETURNED").contains(report.getStatus())) throw error(
      HttpStatus.CONFLICT,
      "Ce rapport est verrouillé. Attendez un retour de votre superviseur."
    );
    checkVersion(report, request.getVersion());
    if (report.getTeam() == null) {
      if (user.getTeam() == null) throw error(
        HttpStatus.CONFLICT,
        "Affectez d'abord une équipe à ce compte."
      );
      report.setTeam(user.getTeam());
    }
    apply(report, request, user);
    report.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.of("Africa/Lagos")));
    return map(reports.saveAndFlush(report));
  }

  @Transactional
  public ReportResponseDto review(
    Long id,
    String action,
    String comment,
    Long version,
    Authentication auth
  ) {
    var reviewer = currentUser(auth);
    var report = accessible(id, auth);
    boolean assigned =
      SalesAccess.has(reviewer, "SUPERVISEUR") &&
      report.getSupervisor() != null &&
      report.getSupervisor().getId().equals(reviewer.getId());
    boolean legacy =
      SalesAccess.has(reviewer, "SUPER_ADMIN") &&
      report.getSupervisor() == null;
    if (!assigned && !legacy) throw error(
      HttpStatus.FORBIDDEN,
      "Examen réservé au superviseur affecté à ce rapport."
    );
    if (report.getSupervisor() != null) users
      .findLockedById(report.getSupervisor().getId())
      .orElseThrow();
    checkVersion(report, version);
    if (!report.getStatus().equals("SUBMITTED")) throw error(
      HttpStatus.CONFLICT,
      "Seul un rapport soumis peut être examiné."
    );
    if (report.getAuthor().getId().equals(reviewer.getId())) throw error(
      HttpStatus.FORBIDDEN,
      "Vous ne pouvez pas examiner votre propre rapport."
    );
    if (!List.of("RETURNED", "CONFIRMED").contains(action)) throw error(
      HttpStatus.BAD_REQUEST,
      "Action inconnue."
    );
    if (
      action.equals("RETURNED") && (comment == null || comment.isBlank())
    ) throw error(
      HttpStatus.BAD_REQUEST,
      "Précisez ce qui doit être confirmé ou corrigé."
    );
    report.setStatus(action);
    report.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.of("Africa/Lagos")));
    report
      .getReviews()
      .add(
        new ReportReview(
          report,
          reviewer,
          action,
          comment == null ? "" : comment.strip()
        )
      );
    return map(reports.saveAndFlush(report));
  }

  @Transactional
  public void deleteReport(Long id, Authentication auth) {
    var report = accessible(id, auth);
    if (!report.getStatus().equals("DRAFT")) throw error(
      HttpStatus.CONFLICT,
      "Seuls les brouillons peuvent être supprimés."
    );
    var user = currentUser(auth);
    if (
      !report.getAuthor().getId().equals(user.getId()) ||
      SalesAccess.has(user, "DIRECTEUR_COMMERCIAL") ||
      SalesAccess.has(user, "SUPERVISEUR")
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Seul l'auteur peut supprimer son brouillon."
    );
    reports.delete(report);
  }

  private void checkVersion(Report r, Long version) {
    if (version == null || !version.equals(r.getVersion())) throw error(
      HttpStatus.CONFLICT,
      "Rapport modifié entre-temps. Actualisez avant de réessayer."
    );
  }

  private User currentUser(Authentication auth) {
    return users
      .findByEmail(auth.getName())
      .filter(User::isEnabled)
      .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Compte indisponible"));
  }

  private boolean isAdmin(Authentication auth) {
    return auth
      .getAuthorities()
      .stream()
      .anyMatch(
        a ->
          a.getAuthority().equals("ROLE_ADMIN") ||
          a.getAuthority().equals("ROLE_SUPER_ADMIN")
      );
  }

  private Report accessible(Long id, Authentication auth) {
    var user = currentUser(auth);
    var report = reports
      .findById(id)
      .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Rapport introuvable"));
    if (!SalesAccess.reads(user, report)) throw error(
      HttpStatus.NOT_FOUND,
      "Rapport introuvable"
    );
    return report;
  }

  private void apply(Report r, ReportRequestDto request, User user) {
    // Lock the author so parallel submissions cannot duplicate the same daily counters.
    users.findLockedById(user.getId()).orElseThrow();
    if (request.getAzoRegistrations() != null) {
      var entries = request.getAzoRegistrations();
      if (
        entries.size() > 500 ||
        (!"AZO".equals(r.getTeam()) && !entries.isEmpty())
      ) throw error(
        HttpStatus.BAD_REQUEST,
        "La liste des inscriptions est réservée aux rapports AZO (500 lignes maximum)."
      );
      var normalized = new java.util.ArrayList<AzoRegistration>();
      for (var entry : entries) {
        if (
          entry == null ||
          entry.firstName() == null ||
          entry.lastName() == null ||
          entry.firstName().isBlank() ||
          entry.lastName().isBlank() ||
          entry.firstName().length() > 100 ||
          entry.lastName().length() > 100 ||
          AzoProfiles.ALL.stream().noneMatch(p ->
            p.code().equals(entry.profileCode())
          )
        ) throw error(
          HttpStatus.BAD_REQUEST,
          "Chaque inscription doit contenir un nom, un prénom et un profil AZO valide."
        );
        normalized.add(
          new AzoRegistration(
            entry.firstName().strip(),
            entry.lastName().strip(),
            entry.profileCode()
          )
        );
      }
      r.getAzoRegistrations().clear();
      r.getAzoRegistrations().addAll(normalized);
    }
    r.setTitle(request.getTitle().strip());
    r.setMode(request.getMode());
    r.setReportDate(
      request.getReportDate() == null
        ? LocalDate.now(ZoneId.of("Africa/Lagos"))
        : request.getReportDate()
    );
    if (r.getReportDate().isAfter(LocalDate.now(ZoneId.of("Africa/Lagos")))) {
      throw error(
        HttpStatus.BAD_REQUEST,
        "La journée du rapport ne peut pas être dans le futur."
      );
    }
    String content =
      request.getContent() == null ? "" : request.getContent().strip();
    if (request.getMode().equals("WRITTEN") && content.isBlank()) throw error(
      HttpStatus.BAD_REQUEST,
      "Racontez votre journée dans le contenu du rapport."
    );
    r.setContent(content);
    r.setStatus(request.getStatus() == null ? "DRAFT" : request.getStatus());
    if ("SUBMITTED".equals(r.getStatus())) {
      var supervisor = user.getSupervisor();
      if (
        SalesAccess.commercial(user) &&
        (supervisor == null ||
          !supervisor.isEnabled() ||
          !SalesAccess.has(supervisor, "SUPERVISEUR") ||
          !java.util.Objects.equals(supervisor.getTeam(), r.getTeam()) ||
          supervisor.getZone() == null ||
          supervisor.getZone().isBlank())
      ) throw error(
        HttpStatus.CONFLICT,
        "Le directeur doit vous affecter à un superviseur actif, une équipe et une zone avant l'envoi."
      );
      if (supervisor != null) {
        users.findLockedById(supervisor.getId()).orElseThrow();
        if (
          summaries.existsBySupervisorIdAndReportDateAndTeam(
            supervisor.getId(),
            r.getReportDate(),
            r.getTeam()
          )
        ) throw error(
          HttpStatus.CONFLICT,
          "La synthèse de cette journée a déjà été transmise. Contactez votre superviseur."
        );
      }
      r.setSupervisor(supervisor);
      r.setZone(supervisor == null ? null : supervisor.getZone());
      boolean duplicate = reports
        .findByAuthorIdOrderByCreatedAtDesc(user.getId())
        .stream()
        .anyMatch(
          other ->
            !java.util.Objects.equals(other.getId(), r.getId()) &&
            other.getReportDate().equals(r.getReportDate()) &&
            java.util.Objects.equals(other.getTeam(), r.getTeam()) &&
            !"DRAFT".equals(other.getStatus())
        );
      if (duplicate) throw error(
        HttpStatus.CONFLICT,
        "Un rapport existe déjà pour cette journée et cette équipe. Corrigez le rapport existant pour éviter un double comptage."
      );
    }
    r.getGoalCounts().clear();
    if (
      !request.getSyncStats() || !"STATS".equals(request.getMode())
    ) r.getGoalCounts().putAll(
      SalesGoals.validate(request.getGoalCounts(), r.getTeam())
    );
    r.setProspectsContacted(0);
    r.setRegistrations(0);
    r.setStatsSource("DECLARED");
    r.setStatsFetchedAt(null);
    r.setReferralCode(user.getReferralCode());
    r.getProfileCounts().clear();
    if (request.getMode().equals("STATS")) {
      if (request.getSyncStats()) {
        if (!r.getTeam().equals(user.getTeam())) throw error(
          HttpStatus.CONFLICT,
          "Le compte doit être affecté à cette équipe pour importer ses statistiques."
        );
        var stats = mobile.fetch(user, r.getReportDate());
        r.getProfileCounts().putAll(
          AzoProfiles.validate(stats.profileCounts(), r.getTeam())
        );
        r.getGoalCounts().putAll(
          SalesGoals.validate(stats.goalCounts(), r.getTeam())
        );
        r.setProspectsContacted(stats.prospectsContacted());
        r.setRegistrations(stats.registrations());
        r.setStatsSource("APP_API");
        r.setStatsFetchedAt(
          LocalDateTime.now(java.time.ZoneId.of("Africa/Lagos"))
        );
      } else {
        r.getProfileCounts().putAll(
          AzoProfiles.validate(request.getProfileCounts(), r.getTeam())
        );
        r.setProspectsContacted(request.getProspectsContacted());
        r.setRegistrations(request.getRegistrations());
      }
    }
  }

  private ReportResponseDto map(Report r) {
    var d = new ReportResponseDto();
    d.setId(r.getId());
    d.setAzoRegistrations(
      r
        .getAzoRegistrations()
        .stream()
        .map(e ->
          new AzoRegistrationDto(
            e.getFirstName(),
            e.getLastName(),
            e.getProfileCode()
          )
        )
        .toList()
    );
    d.setGoalCounts(new java.util.TreeMap<>(r.getGoalCounts()));
    d.setSupervisorId(
      r.getSupervisor() == null ? null : r.getSupervisor().getId()
    );
    d.setZone(r.getZone());
    d.setTitle(r.getTitle());
    d.setContent(r.getContent());
    d.setStatus(r.getStatus());
    d.setCreatedAt(r.getCreatedAt());
    d.setUpdatedAt(r.getUpdatedAt());
    d.setAuthorId(r.getAuthor().getId());
    d.setAuthorName(
      r.getAuthor().getFirstName() + " " + r.getAuthor().getLastName()
    );
    d.setProfileCounts(new java.util.TreeMap<>(r.getProfileCounts()));
    d.setTeam(r.getTeam());
    d.setReportDate(r.getReportDate());
    d.setMode(r.getMode());
    d.setProspectsContacted(r.getProspectsContacted());
    d.setRegistrations(r.getRegistrations());
    d.setStatsSource(r.getStatsSource());
    d.setStatsFetchedAt(r.getStatsFetchedAt());
    d.setReferralCode(r.getReferralCode());
    d.setVersion(r.getVersion());
    d.setReviews(
      r
        .getReviews()
        .stream()
        .map(v ->
          new ReportResponseDto.ReviewDto(
            v.getAction(),
            v.getComment(),
            v.getReviewer().getFirstName() +
              " " +
              v.getReviewer().getLastName(),
            v.getCreatedAt()
          )
        )
        .toList()
    );
    return d;
  }

  private ResponseStatusException error(HttpStatus status, String reason) {
    return new ResponseStatusException(status, reason);
  }
}
