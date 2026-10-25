package com.rapportangi.backend.controller;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping
  public List<ReportResponseDto> list(Authentication auth) {
    return reports.getAllReports(auth);
  }

  @GetMapping("/{id}")
  public ReportResponseDto get(@PathVariable Long id, Authentication auth) {
    return reports.getReportById(id, auth);
  }

  @PostMapping
  public ResponseEntity<ReportResponseDto> create(
    @Valid @RequestBody ReportRequestDto request,
    Authentication auth
  ) {
    var created = reports.createReport(request, auth);
    return ResponseEntity.created(
      java.net.URI.create("/api/reports/" + created.getId())
    ).body(created);
  }

  @PutMapping("/{id}")
  public ReportResponseDto update(
    @PathVariable Long id,
    @Valid @RequestBody ReportRequestDto request,
    Authentication auth
  ) {
    return reports.updateReport(id, request, auth);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
    @PathVariable Long id,
    Authentication auth
  ) {
    reports.deleteReport(id, auth);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/review")
  public ReportResponseDto review(
    @PathVariable Long id,
    @Valid @RequestBody ReviewRequest request,
    Authentication auth
  ) {
    return reports.review(
      id,
      request.action(),
      request.comment(),
      request.version(),
      auth
    );
  }

  public record ReviewRequest(
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Pattern(regexp = "RETURNED|CONFIRMED")
    String action,
    @jakarta.validation.constraints.Size(max = 3000) String comment,
    @jakarta.validation.constraints.NotNull Long version
  ) {}

  @org.springframework.beans.factory.annotation.Autowired
  private com.rapportangi.backend.service.ReportPdfService pdf;

  @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication auth)
    throws java.io.IOException {
    var report = reports.getReportById(id, auth);
    return ResponseEntity.ok()
      .header(
        "Content-Disposition",
        "attachment; filename=rapport-" + id + ".pdf"
      )
      .cacheControl(org.springframework.http.CacheControl.noStore())
      .body(pdf.render(report));
  }
}
