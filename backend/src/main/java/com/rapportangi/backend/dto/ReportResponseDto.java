package com.rapportangi.backend.dto;

import java.time.LocalDateTime;

public class ReportResponseDto {

  private Long id;
  private String title;
  private String content;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Long authorId;
  private String authorName;

  // Constructeurs
  public ReportResponseDto() {}

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

  public Long getAuthorId() {
    return authorId;
  }

  public void setAuthorId(Long authorId) {
    this.authorId = authorId;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName(String authorName) {
    this.authorName = authorName;
  }

  private String team;

  public String getTeam() {
    return team;
  }

  public void setTeam(String value) {
    this.team = value;
  }

  private java.time.LocalDate reportDate;

  public java.time.LocalDate getReportDate() {
    return reportDate;
  }

  public void setReportDate(java.time.LocalDate value) {
    this.reportDate = value;
  }

  private String mode;

  public String getMode() {
    return mode;
  }

  public void setMode(String value) {
    this.mode = value;
  }

  private int prospectsContacted;

  public int getProspectsContacted() {
    return prospectsContacted;
  }

  public void setProspectsContacted(int value) {
    this.prospectsContacted = value;
  }

  private int registrations;

  public int getRegistrations() {
    return registrations;
  }

  public void setRegistrations(int value) {
    this.registrations = value;
  }

  private String statsSource;

  public String getStatsSource() {
    return statsSource;
  }

  public void setStatsSource(String value) {
    this.statsSource = value;
  }

  private LocalDateTime statsFetchedAt;

  public LocalDateTime getStatsFetchedAt() {
    return statsFetchedAt;
  }

  public void setStatsFetchedAt(LocalDateTime value) {
    this.statsFetchedAt = value;
  }

  private String referralCode;

  public String getReferralCode() {
    return referralCode;
  }

  public void setReferralCode(String value) {
    this.referralCode = value;
  }

  private Long version;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    this.version = value;
  }

  private java.util.List<ReviewDto> reviews;

  public java.util.List<ReviewDto> getReviews() {
    return reviews;
  }

  public void setReviews(java.util.List<ReviewDto> value) {
    this.reviews = value;
  }

  public record ReviewDto(
    String action,
    String comment,
    String reviewerName,
    LocalDateTime createdAt
  ) {}

  private java.util.Map<String, Integer> profileCounts =
    new java.util.HashMap<>();

  public java.util.Map<String, Integer> getProfileCounts() {
    return profileCounts;
  }

  public void setProfileCounts(java.util.Map<String, Integer> value) {
    profileCounts = value;
  }

  @jakarta.validation.constraints.Size(max = 5)
  private java.util.Map<String, Integer> goalCounts = new java.util.HashMap<>();

  public java.util.Map<String, Integer> getGoalCounts() {
    return goalCounts;
  }

  public void setGoalCounts(java.util.Map<String, Integer> value) {
    goalCounts = value;
  }

  private Long supervisorId;
  private String zone;

  public Long getSupervisorId() {
    return supervisorId;
  }

  public void setSupervisorId(Long value) {
    supervisorId = value;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String value) {
    zone = value;
  }

  private java.util.List<AzoRegistrationDto> azoRegistrations =
    java.util.List.of();

  public java.util.List<AzoRegistrationDto> getAzoRegistrations() {
    return azoRegistrations;
  }

  public void setAzoRegistrations(java.util.List<AzoRegistrationDto> value) {
    azoRegistrations = value;
  }
}
