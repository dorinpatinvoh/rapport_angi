package com.rapportangi.backend.controller;

import com.rapportangi.backend.dto.ReportRequestDto;
import com.rapportangi.backend.dto.ReportResponseDto;
import com.rapportangi.backend.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*") // Permet à n'importe quel front-end de faire des requêtes sans blocage CORS
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // GET /api/reports : Récupérer tous les rapports
    @GetMapping
    public ResponseEntity<List<ReportResponseDto>> getAllReports() {
        return ResponseEntity.ok(reportService.getAllReports());
    }

    // GET /api/reports/{id} : Récupérer un rapport par son ID
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponseDto> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getReportById(id));
    }

    // POST /api/reports : Créer un nouveau rapport
    @PostMapping
    public ResponseEntity<ReportResponseDto> createReport(@RequestBody ReportRequestDto requestDto) {
        ReportResponseDto createdReport = reportService.createReport(requestDto);
        return new ResponseEntity<>(createdReport, HttpStatus.CREATED);
    }

    // DELETE /api/reports/{id} : Supprimer un rapport
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}