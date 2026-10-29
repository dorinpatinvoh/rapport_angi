package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(nullable = false, unique = true, length = 150)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(
    java.time.ZoneId.of("Africa/Lagos")
  );

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
  )
  private Set<Role> roles = new HashSet<>();

  @Column(name = "country_code", nullable = false, length = 2)
  private String country = "BJ";

  @Column(name = "director_country", length = 2, unique = true)
  private String directorCountry;

  @Column(name = "zone_level", length = 20)
  private String zoneLevel;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "director_projects",
    joinColumns = @JoinColumn(name = "user_id")
  )
  @Column(name = "project_code", length = 20)
  private Set<String> projects = new HashSet<>();

  public String getCountry() {
    return country;
  }

  public void setCountry(String value) {
    country = value;
  }

  public String getDirectorCountry() {
    return directorCountry;
  }

  public void setDirectorCountry(String value) {
    directorCountry = value;
  }

  public String getZoneLevel() {
    return zoneLevel;
  }

  public void setZoneLevel(String value) {
    zoneLevel = value;
  }

  public Set<String> getProjects() {
    return projects;
  }

  public void setProjects(Set<String> values) {
    projects = new HashSet<>(values);
  }

  @Column(name = "zone_config_id")
  private Long zoneConfigId;

  public Long getZoneConfigId() {
    return zoneConfigId;
  }

  public void setZoneConfigId(Long value) {
    zoneConfigId = value;
  }

  // Getters et Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles = new HashSet<>(roles);
  }

  @Column(length = 20)
  private String team;

  public String getTeam() {
    return team;
  }

  public void setTeam(String value) {
    this.team = value;
  }

  @Column(name = "referral_code", length = 100)
  private String referralCode;

  public String getReferralCode() {
    return referralCode;
  }

  public void setReferralCode(String value) {
    this.referralCode = value;
  }

  @jakarta.persistence.Column(name = "security_version", nullable = false)
  private long securityVersion;

  public long getSecurityVersion() {
    return securityVersion;
  }

  public void setSecurityVersion(long value) {
    securityVersion = value;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "supervisor_id")
  private User supervisor;

  @Column(length = 150)
  private String zone;

  public User getSupervisor() {
    return supervisor;
  }

  public void setSupervisor(User value) {
    supervisor = value;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String value) {
    zone = value;
  }
}
