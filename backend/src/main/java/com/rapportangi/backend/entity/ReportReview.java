package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_reviews")
public class ReportReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id", nullable = false)
  private Report report;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewer_id", nullable = false)
  private User reviewer;

  @Column(nullable = false, length = 20)
  private String action;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String comment;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now(
    java.time.ZoneId.of("Africa/Lagos")
  );

  public ReportReview() {}

  public ReportReview(
    Report report,
    User reviewer,
    String action,
    String comment
  ) {
    this.report = report;
    this.reviewer = reviewer;
    this.action = action;
    this.comment = comment;
  }

  public Long getId() {
    return id;
  }

  public User getReviewer() {
    return reviewer;
  }

  public String getAction() {
    return action;
  }

  public String getComment() {
    return comment;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
