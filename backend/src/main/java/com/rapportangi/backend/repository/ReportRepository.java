package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    // Permet de récupérer tous les rapports créés par un utilisateur spécifique
    List<Report> findByAuthorId(Long authorId);
}