package com.rapportangi.backend.service;

import com.rapportangi.backend.dto.ReportRequestDto;
import com.rapportangi.backend.dto.ReportResponseDto;
import com.rapportangi.backend.entity.Report;
import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.repository.ReportRepository;
import com.rapportangi.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    // Récupérer tous les rapports
    public List<ReportResponseDto> getAllReports() {
        return reportRepository.findAll()
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    // Récupérer un rapport par son ID
    public ReportResponseDto getReportById(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rapport non trouvé avec l'id: " + id));
        return mapToResponseDto(report);
    }

    // Créer un nouveau rapport
    public ReportResponseDto createReport(ReportRequestDto requestDto) {
        User author = userRepository.findById(requestDto.getAuthorId())
                .orElseThrow(() -> new RuntimeException("Auteur non trouvé avec l'id: " + requestDto.getAuthorId()));

        Report report = new Report();
        report.setTitle(requestDto.getTitle());
        report.setContent(requestDto.getContent());
        report.setStatus(requestDto.getStatus());
        report.setAuthor(author);
        report.setCreatedAt(LocalDateTime.now());

        Report savedReport = reportRepository.save(report);
        return mapToResponseDto(savedReport);
    }

    // Supprimer un rapport
    public void deleteReport(Long id) {
        if (!reportRepository.existsById(id)) {
            throw new RuntimeException("Rapport introuvable avec l'id: " + id);
        }
        reportRepository.deleteById(id);
    }

    // Méthode utilitaire pour convertir l'Entité en DTO
    private ReportResponseDto mapToResponseDto(Report report) {
        ReportResponseDto dto = new ReportResponseDto();
        dto.setId(report.getId());
        dto.setTitle(report.getTitle());
        dto.setContent(report.getContent());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setUpdatedAt(report.getUpdatedAt());

        if (report.getAuthor() != null) {
            dto.setAuthorId(report.getAuthor().getId());
            dto.setAuthorName(report.getAuthor().getFirstName() + " " + report.getAuthor().getLastName());
        }
        return dto;
    }
}