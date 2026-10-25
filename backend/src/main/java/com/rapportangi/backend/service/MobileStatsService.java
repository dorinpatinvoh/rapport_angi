package com.rapportangi.backend.service;

import com.rapportangi.backend.entity.User;
import java.net.http.HttpClient;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MobileStatsService {

  private final Map<String, String> urls, keys;
  private final RestClient client;

  public MobileStatsService(
    @Value("${app.integrations.azo.url:}") String azoUrl,
    @Value("${app.integrations.azo.key:}") String azoKey,
    @Value("${app.integrations.zehami.url:}") String zehamiUrl,
    @Value("${app.integrations.zehami.key:}") String zehamiKey
  ) {
    urls = Map.of("AZO", azoUrl, "ZEHAMI", zehamiUrl);
    keys = Map.of("AZO", azoKey, "ZEHAMI", zehamiKey);
    var factory = new JdkClientHttpRequestFactory(
      HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    );
    factory.setReadTimeout(Duration.ofSeconds(5));
    client = RestClient.builder().requestFactory(factory).build();
  }

  public boolean configured(String team) {
    return (
      team != null &&
      !urls.getOrDefault(team, "").isBlank() &&
      !keys.getOrDefault(team, "").isBlank()
    );
  }

  public Stats fetch(User user, LocalDate date) {
    if (!configured(user.getTeam())) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Connexion à l'application mobile non configurée pour cette équipe."
    );
    if (
      user.getReferralCode() == null || user.getReferralCode().isBlank()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Le super administrateur doit renseigner votre code de parrainage."
    );
    var uri = UriComponentsBuilder.fromUriString(urls.get(user.getTeam()))
      .queryParam("referralCode", user.getReferralCode())
      .queryParam("date", date)
      .build()
      .encode()
      .toUri();
    if (
      !"https".equals(uri.getScheme()) &&
      !(
        "http".equals(uri.getScheme()) &&
        ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
      )
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "L'API mobile doit utiliser HTTPS."
    );
    try {
      var stats = client
        .get()
        .uri(uri)
        .headers(h -> h.setBearerAuth(keys.get(user.getTeam())))
        .retrieve()
        .body(Stats.class);
      if (
        stats == null ||
        !date.equals(stats.date()) ||
        !user.getTeam().equals(stats.team()) ||
        !user.getReferralCode().equals(stats.referralCode()) ||
        stats.prospectsContacted() == null ||
        stats.registrations() == null ||
        stats.prospectsContacted() < 0 ||
        stats.registrations() < 0 ||
        stats.prospectsContacted() > 1000000 ||
        stats.registrations() > 1000000
      ) throw new IllegalArgumentException();
      return stats;
    } catch (Exception ex) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Statistiques mobiles indisponibles ou réponse incompatible. Réessayez plus tard."
      );
    }
  }

  public record Stats(
    String team,
    String referralCode,
    LocalDate date,
    Integer prospectsContacted,
    Integer registrations
  ) {}
}
