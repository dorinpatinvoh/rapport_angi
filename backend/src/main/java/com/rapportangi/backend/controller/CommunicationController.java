package com.rapportangi.backend.controller;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/communications")
public class CommunicationController {

  private final CommunicationService comms;
  private final LiveUpdates live;
  private final SalesAccess access;

  public CommunicationController(
    CommunicationService c,
    LiveUpdates l,
    SalesAccess a
  ) {
    comms = c;
    live = l;
    access = a;
  }

  @GetMapping("/notifications")
  public List<ActivityEvent> notifications(Authentication a) {
    return comms.events(a);
  }

  @GetMapping("/announcements")
  public List<Announcement> announcements(Authentication a) {
    return comms.announcements(a);
  }

  public record Publish(
    @NotBlank @Size(max = 160) String title,
    @NotBlank @Size(max = 8000) String content,
    @Pattern(regexp = "[A-Z]{2}") String country,
    @NotNull
    @Size(max = 2)
    Set<@NotNull @Pattern(regexp = "AZO|ZEHAMI") String> projects
  ) {}

  @PostMapping("/announcements")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTEUR_COMMERCIAL')")
  public Announcement publish(@Valid @RequestBody Publish r, Authentication a) {
    return comms.publish(r.title(), r.content(), r.country(), r.projects(), a);
  }

  @GetMapping(value = "/stream", produces = "text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
    Authentication a,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    access.current(a);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Accel-Buffering", "no");
    return live.connect(request.getSession(false));
  }
}
