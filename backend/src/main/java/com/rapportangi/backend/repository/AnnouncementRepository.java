package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.Announcement;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository
  extends JpaRepository<Announcement, Long>
{
  @Query(
    "select distinct a from Announcement a left join a.projects p where :global=true or ((a.country is null or a.country=:country) and (p is null or p in :projects)) order by a.id desc"
  )
  java.util.List<Announcement> visible(
    @Param("global") boolean global,
    @Param("country") String country,
    @Param("projects") java.util.Set<String> projects,
    org.springframework.data.domain.Pageable page
  );
}
