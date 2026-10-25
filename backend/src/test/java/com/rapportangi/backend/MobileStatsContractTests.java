package com.rapportangi.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.rapportangi.backend.entity.User;
import com.rapportangi.backend.service.MobileStatsService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MobileStatsContractTests {

  @Test
  void configuredApiValidatesIdentityDateAndCounts() throws Exception {
    var response = new AtomicReference<>(
      "{\"team\":\"AZO\",\"referralCode\":\"CODE-1\",\"date\":\"2026-10-25\",\"prospectsContacted\":14,\"registrations\":4}"
    );
    var receivedAuth = new AtomicReference<String>();
    var receivedQuery = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/stats", exchange -> {
      receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      receivedQuery.set(exchange.getRequestURI().getQuery());
      var bytes = response
        .get()
        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    try {
      var mobile = new MobileStatsService(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/stats",
        "test-secret",
        "",
        ""
      );
      var user = new User();
      user.setTeam("AZO");
      user.setReferralCode("CODE-1");
      var result = mobile.fetch(user, LocalDate.of(2026, 10, 25));
      assertEquals(4, result.registrations());
      assertEquals("Bearer test-secret", receivedAuth.get());
      assertTrue(receivedQuery.get().contains("referralCode=CODE-1"));
      assertTrue(receivedQuery.get().contains("date=2026-10-25"));
      String valid = response.get();
      for (String invalid : new String[] {
        valid.replace("CODE-1", "OTHER"),
        valid.replace("2026-10-25", "2026-10-24"),
        valid.replace("\"registrations\":4", "\"registrations\":-1"),
      }) {
        response.set(invalid);
        assertThrows(ResponseStatusException.class, () ->
          mobile.fetch(user, LocalDate.of(2026, 10, 25))
        );
      }
    } finally {
      server.stop(0);
    }
  }
}
