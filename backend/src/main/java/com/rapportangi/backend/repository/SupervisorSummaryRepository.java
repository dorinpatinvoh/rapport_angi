package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.SupervisorSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupervisorSummaryRepository
  extends JpaRepository<SupervisorSummary, Long>
{
  boolean existsBySupervisorIdAndReportDateAndTeam(
    Long id,
    java.time.LocalDate date,
    String team
  );
}
