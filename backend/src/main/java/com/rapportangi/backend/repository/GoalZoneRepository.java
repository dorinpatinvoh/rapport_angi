package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.GoalZone;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalZoneRepository extends JpaRepository<GoalZone, Long> {
  List<GoalZone> findByCountryAndProjectAndZoneKey(
    String country,
    String project,
    String zoneKey
  );
  Optional<GoalZone> findByCountryAndProjectAndLevelAndZoneKey(
    String country,
    String project,
    String level,
    String zoneKey
  );
}
