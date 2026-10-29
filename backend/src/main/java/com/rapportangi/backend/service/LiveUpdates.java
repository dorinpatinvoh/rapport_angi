package com.rapportangi.backend.service;

import jakarta.servlet.http.HttpSession;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@EnableScheduling
public class LiveUpdates {

  public record Changed() {}

  private final ConcurrentHashMap<SseEmitter, HttpSession> clients =
    new ConcurrentHashMap<>();

  public SseEmitter connect(HttpSession session) {
    if (session == null) throw SalesAccess.error(
      org.springframework.http.HttpStatus.UNAUTHORIZED,
      "Session requise."
    );
    if (clients.size() >= 500) throw SalesAccess.error(
      org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
      "Réessayez dans quelques instants."
    );
    var emitter = new SseEmitter(25000L);
    clients.put(emitter, session);
    Runnable remove = () -> clients.remove(emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(() -> {
      remove.run();
      emitter.complete();
    });
    emitter.onError(e -> remove.run());
    send(emitter, session, true);
    return emitter;
  }

  private void send(SseEmitter emitter, HttpSession session, boolean refresh) {
    try {
      session.getCreationTime(); // Throws after logout / invalidation.
      emitter.send(
        refresh
          ? SseEmitter.event()
              .name("refresh")
              .data("refresh")
              .reconnectTime(3000)
          : SseEmitter.event().comment("heartbeat")
      );
    } catch (Exception ex) {
      clients.remove(emitter);
      emitter.complete();
    }
  }

  // No user data is sent on the stream. The client reloads its authorized REST feed.
  @TransactionalEventListener
  public void changed(Changed event) {
    clients.forEach((e, s) -> send(e, s, true));
  }

  @Scheduled(fixedDelay = 15000)
  public void heartbeat() {
    clients.forEach((e, s) -> send(e, s, false));
  }
}
