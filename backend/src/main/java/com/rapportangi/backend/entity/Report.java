package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false, length = 50)
  private String status = "DRAFT";

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(
    java.time.ZoneId.of("Africa/Lagos")
  );

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  // Getters et Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public User getAuthor() {
    return author;
  }

  public void setAuthor(User author) {
    this.author = author;
  }

  @Column(length = 20)
  private String team;

  public String getTeam() {
    return team;
  }

  public void setTeam(String value) {
    this.team = value;
  }

  @Column(name = "report_date", nullable = false)
  private java.time.LocalDate reportDate = java.time.LocalDate.now(
    java.time.ZoneId.of("Africa/Lagos")
  );

  public java.time.LocalDate getReportDate() {
    return reportDate;
  }

  public void setReportDate(java.time.LocalDate value) {
    this.reportDate = value;
  }

  @Column(nullable = false, length = 20)
  private String mode = "WRITTEN";

  public String getMode() {
    return mode;
  }

  public void setMode(String value) {
    this.mode = value;
  }

  @Column(name = "prospects_contacted", nullable = false)
  private int prospectsContacted;

  public int getProspectsContacted() {
    return prospectsContacted;
  }

  public void setProspectsContacted(int value) {
    this.prospectsContacted = value;
  }

  @Column(nullable = false)
  private int registrations;

  public int getRegistrations() {
    return registrations;
  }

  public void setRegistrations(int value) {
    this.registrations = value;
  }

  @Column(name = "stats_source", nullable = false, length = 20)
  private String statsSource = "DECLARED";

  public String getStatsSource() {
    return statsSource;
  }

  public void setStatsSource(String value) {
    this.statsSource = value;
  }

  @Column(name = "stats_fetched_at")
  private LocalDateTime statsFetchedAt;

  public LocalDateTime getStatsFetchedAt() {
    return statsFetchedAt;
  }

  public void setStatsFetchedAt(LocalDateTime value) {
    this.statsFetchedAt = value;
  }

  @Column(name = "referral_code", length = 100)
  private String referralCode;

  public String getReferralCode() {
    return referralCode;
  }

  public void setReferralCode(String value) {
    this.referralCode = value;
  }

  @Version
  private Long version = 0L;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    this.version = value;
  }

  @OneToMany(
    mappedBy = "report",
    cascade = CascadeType.ALL,
    orphanRemoval = true
  )
  @OrderBy("id ASC")
  private java.util.List<ReportReview> reviews = new java.util.ArrayList<>();

  public java.util.List<ReportReview> getReviews() {
    return reviews;
  }

  public void setReviews(java.util.List<ReportReview> value) {
    this.reviews = value;
  }
}
