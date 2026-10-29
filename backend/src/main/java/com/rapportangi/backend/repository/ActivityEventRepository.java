package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.ActivityEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ActivityEventRepository
  extends JpaRepository<ActivityEvent, Long>
{
  @Query(
    "select e from ActivityEvent e where :global=true or ((e.country is null or e.country=:country) and (e.project is null or e.project in :projects)) order by e.id desc"
  )
  java.util.List<ActivityEvent> visible(
    @Param("global") boolean global,
    @Param("country") String country,
    @Param("projects") java.util.Set<String> projects,
    org.springframework.data.domain.Pageable page
  );
}
