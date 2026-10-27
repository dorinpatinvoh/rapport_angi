package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(
  name = "supervisor_summaries",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "supervisor_id", "report_date", "team" }
  )
)
public class SupervisorSummary {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "supervisor_id", nullable = false)
  public User supervisor;

  @Column(name = "report_date", nullable = false)
  public LocalDate reportDate;

  @Column(nullable = false, length = 20)
  public String team;

  @Column(nullable = false, length = 150)
  public String zone;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String content;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Africa/Lagos"));

  @ManyToMany
  @JoinTable(
    name = "summary_reports",
    joinColumns = @JoinColumn(name = "summary_id"),
    inverseJoinColumns = @JoinColumn(name = "report_id")
  )
  public Set<Report> reports = new HashSet<>();
}
