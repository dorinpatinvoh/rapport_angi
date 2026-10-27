package com.rapportangi.backend.dto;

import jakarta.validation.constraints.*;

public class ReportRequestDto {

  @NotBlank
  @Size(max = 200)
  private String title;

  @Size(max = 50000)
  private String content = "";

  @Pattern(regexp = "DRAFT|SUBMITTED")
  private String status;

  private Long authorId;

  // Constructeurs
  public ReportRequestDto() {}

  public ReportRequestDto(
    String title,
    String content,
    String status,
    Long authorId
  ) {
    this.title = title;
    this.content = content;
    this.status = status;
    this.authorId = authorId;
  }

  // Getters et Setters
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

  public Long getAuthorId() {
    return authorId;
  }

  public void setAuthorId(Long authorId) {
    this.authorId = authorId;
  }

  @Pattern(regexp = "AZO|ZEHAMI")
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

  @NotNull
  @Pattern(regexp = "WRITTEN|STATS")
  private String mode = "WRITTEN";

  public String getMode() {
    return mode;
  }

  public void setMode(String value) {
    this.mode = value;
  }

  @NotNull
  @Min(0)
  @Max(1000000)
  private Integer prospectsContacted = 0;

  public Integer getProspectsContacted() {
    return prospectsContacted;
  }

  public void setProspectsContacted(Integer value) {
    this.prospectsContacted = value;
  }

  @NotNull
  @Min(0)
  @Max(1000000)
  private Integer registrations = 0;

  public Integer getRegistrations() {
    return registrations;
  }

  public void setRegistrations(Integer value) {
    this.registrations = value;
  }

  private boolean syncStats;

  public boolean getSyncStats() {
    return syncStats;
  }

  public void setSyncStats(boolean value) {
    this.syncStats = value;
  }

  private Long version;

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long value) {
    this.version = value;
  }

  @jakarta.validation.constraints.Size(max = 16)
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
}
