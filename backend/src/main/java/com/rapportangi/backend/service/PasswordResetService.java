package com.rapportangi.backend.service;

import com.rapportangi.backend.entity.*;
import com.rapportangi.backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

  private final UserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final ObjectProvider<JavaMailSender> sender;
  private final String host, from, publicUrl;
  private final SalesAccess access;
  private final CommunicationService comms;
  private final Map<String, Instant> cooldown = new LinkedHashMap<>();

  public PasswordResetService(
    UserRepository users,
    PasswordResetTokenRepository tokens,
    PasswordEncoder encoder,
    ObjectProvider<JavaMailSender> sender,
    @Value("${spring.mail.host:}") String host,
    @Value("${app.mail.from:}") String from,
    @Value("${app.public-url:http://localhost:4200}") String publicUrl,
    SalesAccess access,
    CommunicationService comms
  ) {
    this.users = users;
    this.access = access;
    this.comms = comms;
    this.tokens = tokens;
    this.encoder = encoder;
    this.sender = sender;
    this.host = host;
    this.from = from;
    this.publicUrl = publicUrl.replaceAll("/$", "");
  }

  @Transactional
  public void request(String email) {
    if (
      host.isBlank() || from.isBlank() || sender.getIfAvailable() == null
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "La réinitialisation par email n'est pas encore configurée. Contactez le super administrateur."
    );
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    synchronized (cooldown) {
      Instant last = cooldown.get(normalized);
      if (last != null && last.plusSeconds(60).isAfter(Instant.now())) return;
      if (cooldown.size() >= 4096) cooldown.remove(
        cooldown.keySet().iterator().next()
      );
      cooldown.put(normalized, Instant.now());
    }
    var user = users.findByEmailIgnoreCase(normalized).filter(User::isEnabled);
    if (user.isEmpty()) return;
    users.findLockedById(user.get().getId()).orElseThrow();
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    tokens.deleteByUserId(user.get().getId());
    tokens.saveAndFlush(
      new PasswordResetToken(
        user.get(),
        hash(raw),
        Instant.now().plusSeconds(1800)
      )
    );
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(user.get().getEmail());
    message.setSubject("ANGI DSN PRO - Réinitialiser votre mot de passe");
    message.setText(
      "Une réinitialisation de votre mot de passe a été demandée.\n\n" +
        publicUrl +
        "/reinitialiser-mot-de-passe#token=" +
        raw +
        "\n\nCe lien est valable 30 minutes et utilisable une seule fois. Si vous n'avez pas fait cette demande, ignorez cet email."
    );
    try {
      sender.getObject().send(message);
    } catch (org.springframework.mail.MailException ex) {
      tokens.deleteByUserId(user.get().getId());
      org.slf4j.LoggerFactory.getLogger(getClass()).warn(
        "Email de réinitialisation non remis. Vérifiez la configuration SMTP."
      );
    }
  }

  @Transactional
  public void reset(String token, String password) {
    var record = tokens
      .findLocked(hash(token))
      .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Lien invalide ou expiré. Demandez un nouveau lien."
        )
      );
    var user = users.findLockedById(record.getUser().getId()).orElseThrow();
    if (!user.isEnabled()) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Lien invalide ou expiré."
    );
    change(user, password);
    tokens.deleteByUserId(user.getId());
  }

  @Transactional
  public void resetCommercial(
    Long id,
    String password,
    org.springframework.security.core.Authentication auth
  ) {
    var actor = access.current(auth);
    if (!SalesAccess.has(actor, "SUPER_ADMIN")) throw SalesAccess.error(
      HttpStatus.FORBIDDEN,
      "Administration globale requise."
    );
    resetCommercial(id, password);
    var target = users.findById(id).orElseThrow();
    comms.record(
      actor,
      "PASSWORD_RESET",
      "Accès au compte de " +
        target.getFirstName() +
        " " +
        target.getLastName() +
        " réinitialisé.",
      target.getCountry(),
      target.getTeam()
    );
  }

  @Transactional
  public void resetCommercial(Long id, String password) {
    var user = users
      .findLockedById(id)
      .filter(SalesAccess::commercial)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Commercial introuvable"
        )
      );
    change(user, password);
    tokens.deleteByUserId(id);
  }

  private void change(User user, String password) {
    if (
      password == null ||
      password.length() < 12 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Choisissez un mot de passe d'au moins 12 caractères, limité à 72 octets UTF-8."
    );
    user.setPassword(encoder.encode(password));
    user.setSecurityVersion(user.getSecurityVersion() + 1);
    users.saveAndFlush(user);
  }

  private String hash(String raw) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          raw.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
