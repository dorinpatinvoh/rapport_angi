package com.rapportangi.backend.service;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final ReportRepository reports;
    private final UserRepository users;
    public ReportService(ReportRepository reports, UserRepository users) { this.reports = reports; this.users = users; }

    public List<ReportResponseDto> getAllReports(Authentication auth) {
        var user = currentUser(auth);
        return (isAdmin(auth) ? reports.findAll(org.springframework.data.domain.Sort.by("createdAt").descending())
                : reports.findByAuthorIdOrderByCreatedAtDesc(user.getId())).stream().map(this::map).toList();
    }
    public ReportResponseDto getReportById(Long id, Authentication auth) { return map(accessible(id, auth)); }

    @Transactional
    public ReportResponseDto createReport(ReportRequestDto request, Authentication auth) {
        var report = new Report();
        report.setAuthor(currentUser(auth));
        apply(report, request);
        return map(reports.save(report));
    }
    @Transactional
    public ReportResponseDto updateReport(Long id, ReportRequestDto request, Authentication auth) {
        var report = accessible(id, auth);
        apply(report, request);
        report.setUpdatedAt(LocalDateTime.now());
        return map(reports.save(report));
    }
    @Transactional
    public void deleteReport(Long id, Authentication auth) { reports.delete(accessible(id, auth)); }

    private User currentUser(Authentication auth) {
        return users.findByEmail(auth.getName()).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte indisponible"));
    }
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
    private Report accessible(Long id, Authentication auth) {
        var user = currentUser(auth);
        var report = reports.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapport introuvable"));
        if (!isAdmin(auth) && !report.getAuthor().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapport introuvable");
        return report;
    }
    private void apply(Report report, ReportRequestDto request) {
        report.setTitle(request.getTitle().strip()); report.setContent(request.getContent().strip());
        report.setStatus(request.getStatus() == null ? "DRAFT" : request.getStatus());
    }
    private ReportResponseDto map(Report report) {
        var dto = new ReportResponseDto();
        dto.setId(report.getId()); dto.setTitle(report.getTitle()); dto.setContent(report.getContent());
        dto.setStatus(report.getStatus()); dto.setCreatedAt(report.getCreatedAt()); dto.setUpdatedAt(report.getUpdatedAt());
        dto.setAuthorId(report.getAuthor().getId());
        dto.setAuthorName(report.getAuthor().getFirstName() + " " + report.getAuthor().getLastName());
        return dto;
    }
}
