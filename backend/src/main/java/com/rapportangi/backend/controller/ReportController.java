package com.rapportangi.backend.controller;

import com.rapportangi.backend.dto.*;
import com.rapportangi.backend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports = reports; }
    @GetMapping public List<ReportResponseDto> list(Authentication auth) { return reports.getAllReports(auth); }
    @GetMapping("/{id}") public ReportResponseDto get(@PathVariable Long id, Authentication auth) {
        return reports.getReportById(id, auth);
    }
    @PostMapping public ResponseEntity<ReportResponseDto> create(@Valid @RequestBody ReportRequestDto request, Authentication auth) {
        var created = reports.createReport(request, auth);
        return ResponseEntity.created(java.net.URI.create("/api/reports/" + created.getId())).body(created);
    }
    @PutMapping("/{id}") public ReportResponseDto update(@PathVariable Long id,
            @Valid @RequestBody ReportRequestDto request, Authentication auth) { return reports.updateReport(id, request, auth); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        reports.deleteReport(id, auth); return ResponseEntity.noContent().build();
    }
}
