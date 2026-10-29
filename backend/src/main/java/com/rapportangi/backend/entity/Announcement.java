package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "announcements")
public class Announcement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "author_id", nullable = false)
  public Long authorId;

  @Column(name = "author_name", nullable = false, length = 210)
  public String authorName;

  @Column(nullable = false, length = 160)
  public String title;

  @Column(nullable = false, length = 8000)
  public String content;

  @Column(name = "country_code", length = 2)
  public String country;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Africa/Lagos"));

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "announcement_projects",
    joinColumns = @JoinColumn(name = "announcement_id")
  )
  @Column(name = "project_code", length = 20)
  public Set<String> projects = new HashSet<>();
}
