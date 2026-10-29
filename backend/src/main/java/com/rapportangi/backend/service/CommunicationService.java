package com.rapportangi.backend.service;

import static com.rapportangi.backend.service.SalesAccess.*;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunicationService {

  private final ActivityEventRepository events;
  private final AnnouncementRepository announcements;
  private final SalesAccess access;
  private final SalesCountryRepository countries;
  private final ApplicationEventPublisher publisher;

  public CommunicationService(
    ActivityEventRepository events,
    AnnouncementRepository announcements,
    SalesAccess access,
    SalesCountryRepository countries,
    ApplicationEventPublisher publisher
  ) {
    this.events = events;
    this.announcements = announcements;
    this.access = access;
    this.countries = countries;
    this.publisher = publisher;
  }

  public void record(
    User actor,
    String action,
    String message,
    String country,
    String project
  ) {
    var e = new ActivityEvent();
    e.actorId = actor.getId();
    e.actorName = actor.getFirstName() + " " + actor.getLastName();
    e.action = action;
    e.message = message;
    e.country = country;
    e.project = project;
    events.save(e);
    publisher.publishEvent(new LiveUpdates.Changed());
  }

  public static Set<String> projects(User u) {
    if (has(u, "DIRECTEUR_COMMERCIAL")) return u.getDirectorCountry() == null ||
      u.getProjects().isEmpty()
      ? Set.of("__NONE__")
      : u.getProjects();
    return u.getTeam() == null ? Set.of("__NONE__") : Set.of(u.getTeam());
  }

  public List<ActivityEvent> events(Authentication auth) {
    var u = access.current(auth);
    return events.visible(
      has(u, "SUPER_ADMIN"),
      has(u, "DIRECTEUR_COMMERCIAL") ? u.getDirectorCountry() : u.getCountry(),
      projects(u),
      PageRequest.of(0, 50)
    );
  }

  public List<Announcement> announcements(Authentication auth) {
    var u = access.current(auth);
    return announcements.visible(
      has(u, "SUPER_ADMIN"),
      has(u, "DIRECTEUR_COMMERCIAL") ? u.getDirectorCountry() : u.getCountry(),
      projects(u),
      PageRequest.of(0, 50)
    );
  }

  public Announcement publish(
    String title,
    String content,
    String country,
    Set<String> projects,
    Authentication auth
  ) {
    var u = access.current(auth);
    if (!director(u)) throw error(
      HttpStatus.FORBIDDEN,
      "Diffusion réservée à la direction."
    );
    if (country != null && !countries.existsById(country)) throw error(
      HttpStatus.BAD_REQUEST,
      "Pays inconnu."
    );
    if (!Set.of("AZO", "ZEHAMI").containsAll(projects)) throw error(
      HttpStatus.BAD_REQUEST,
      "Projet inconnu."
    );
    if (
      !has(u, "SUPER_ADMIN") &&
      (!Objects.equals(u.getDirectorCountry(), country) ||
        country == null ||
        projects.isEmpty() ||
        !u.getProjects().containsAll(projects))
    ) throw error(
      HttpStatus.FORBIDDEN,
      "Communiqué hors de votre pays ou de vos projets."
    );
    var a = new Announcement();
    a.authorId = u.getId();
    a.authorName = u.getFirstName() + " " + u.getLastName();
    a.title = title.strip();
    a.content = content.strip();
    a.country = country;
    a.projects.addAll(projects);
    announcements.save(a);
    if (projects.isEmpty()) record(
      u,
      "ANNOUNCEMENT_PUBLISHED",
      "Communiqué publié : " + a.title,
      country,
      null
    );
    else for (String project : projects)
      record(
        u,
        "ANNOUNCEMENT_PUBLISHED",
        "Communiqué publié : " + a.title,
        country,
        project
      );
    return a;
  }
}
