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

  public ReportService(
    ReportRepository reports,
    UserRepository users,
    MobileStatsService mobile
  ) {
    this.reports = reports;
    this.users = users;
    this.mobile = mobile;
  }

  public List<ReportResponseDto> getAllReports(Authentication auth) {
    var user = currentUser(auth);
    return (
      isAdmin(auth)
        ? reports.findAll(
            org.springframework.data.domain.Sort.by("createdAt").descending()
          )
        : reports.findByAuthorIdOrderByCreatedAtDesc(user.getId())
    )
      .stream()
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
    if (!report.getAuthor().getId().equals(user.getId())) throw error(
      HttpStatus.FORBIDDEN,
      "Seul l'auteur peut modifier le contenu."
    );
    if (!List.of("DRAFT", "RETURNED").contains(report.getStatus())) throw error(
      HttpStatus.CONFLICT,
      "Ce rapport est verrouillé. Attendez un retour du super administrateur."
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
    if (
      auth
        .getAuthorities()
        .stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))
    ) throw error(HttpStatus.FORBIDDEN, "Réservé au super administrateur.");
    var report = accessible(id, auth);
    checkVersion(report, version);
    if (!report.getStatus().equals("SUBMITTED")) throw error(
      HttpStatus.CONFLICT,
      "Seul un rapport soumis peut être examiné."
    );
    var reviewer = currentUser(auth);
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
    if (
      !isAdmin(auth) && !report.getAuthor().getId().equals(user.getId())
    ) throw error(HttpStatus.NOT_FOUND, "Rapport introuvable");
    return report;
  }

  private void apply(Report r, ReportRequestDto request, User user) {
    r.setTitle(request.getTitle().strip());
    r.setMode(request.getMode());
    r.setReportDate(
      request.getReportDate() == null
        ? LocalDate.now(ZoneId.of("Africa/Lagos"))
        : request.getReportDate()
    );
    String content =
      request.getContent() == null ? "" : request.getContent().strip();
    if (request.getMode().equals("WRITTEN") && content.isBlank()) throw error(
      HttpStatus.BAD_REQUEST,
      "Racontez votre journée dans le contenu du rapport."
    );
    r.setContent(content);
    r.setStatus(request.getStatus() == null ? "DRAFT" : request.getStatus());
    r.setProspectsContacted(0);
    r.setRegistrations(0);
    r.setStatsSource("DECLARED");
    r.setStatsFetchedAt(null);
    r.setReferralCode(user.getReferralCode());
    if (request.getMode().equals("STATS")) {
      if (request.getSyncStats()) {
        if (!r.getTeam().equals(user.getTeam())) throw error(
          HttpStatus.CONFLICT,
          "Le compte doit être affecté à cette équipe pour importer ses statistiques."
        );
        var stats = mobile.fetch(user, r.getReportDate());
        r.setProspectsContacted(stats.prospectsContacted());
        r.setRegistrations(stats.registrations());
        r.setStatsSource("APP_API");
        r.setStatsFetchedAt(
          LocalDateTime.now(java.time.ZoneId.of("Africa/Lagos"))
        );
      } else {
        r.setProspectsContacted(request.getProspectsContacted());
        r.setRegistrations(request.getRegistrations());
      }
    }
  }

  private ReportResponseDto map(Report r) {
    var d = new ReportResponseDto();
    d.setId(r.getId());
    d.setTitle(r.getTitle());
    d.setContent(r.getContent());
    d.setStatus(r.getStatus());
    d.setCreatedAt(r.getCreatedAt());
    d.setUpdatedAt(r.getUpdatedAt());
    d.setAuthorId(r.getAuthor().getId());
    d.setAuthorName(
      r.getAuthor().getFirstName() + " " + r.getAuthor().getLastName()
    );
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
