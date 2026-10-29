package com.rapportangi.backend.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "goal_zones")
@SecondaryTable(
  name = "zone_goal_grids",
  pkJoinColumns = @PrimaryKeyJoinColumn(name = "zone_id")
)
public class GoalZone {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "country_code", nullable = false, length = 2)
  public String country;

  @Column(name = "project_code", nullable = false, length = 20)
  public String project;

  @Column(name = "zone_level", nullable = false, length = 20)
  public String level;

  @Column(nullable = false, length = 150)
  public String name;

  @Column(name = "zone_key", nullable = false, length = 200)
  public String zoneKey;

  @Column(name = "parent_id")
  public Long parentId;

  @Version
  public long version;

  @Column(table = "zone_goal_grids", name = "planned_staff", nullable = false)
  public int plannedStaff;

  @Column(table = "zone_goal_grids", name = "inherit_goals", nullable = false)
  public boolean inheritGoals;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
    name = "zone_goal_metrics",
    joinColumns = @JoinColumn(name = "zone_id")
  )
  @OrderColumn(name = "position")
  public List<ZoneGoal> goals = new ArrayList<>();
}
