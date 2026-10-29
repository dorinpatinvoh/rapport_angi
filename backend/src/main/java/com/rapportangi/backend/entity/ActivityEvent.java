package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "activity_events")
public class ActivityEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "actor_name", nullable = false, length = 210)
  public String actorName;

  @Column(nullable = false, length = 50)
  public String action;

  @Column(nullable = false, length = 1000)
  public String message;

  @Column(name = "country_code", length = 2)
  public String country;

  @Column(name = "project_code", length = 20)
  public String project;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Africa/Lagos"));
}
