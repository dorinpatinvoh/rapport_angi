package com.rapportangi.backend.config;

import com.rapportangi.backend.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class SessionAccount
  extends org.springframework.security.core.userdetails.User
{

  private final long securityVersion;

  public SessionAccount(User u) {
    super(
      u.getEmail(),
      u.getPassword(),
      u.isEnabled(),
      true,
      true,
      true,
      u
        .getRoles()
        .stream()
        .map(r ->
          new SimpleGrantedAuthority(
            "ROLE_" + r.getName().replaceFirst("^ROLE_", "")
          )
        )
        .toList()
    );
    securityVersion = u.getSecurityVersion();
  }

  public long getSecurityVersion() {
    return securityVersion;
  }
}
