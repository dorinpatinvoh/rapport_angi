package com.rapportangi.backend.config;

import com.rapportangi.backend.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAccountFilter extends OncePerRequestFilter {

  private final UserRepository users;

  public SessionAccountFilter(UserRepository users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (
      auth != null && auth.getPrincipal() instanceof SessionAccount principal
    ) {
      var user = users.findByEmail(principal.getUsername());
      if (
        user.isEmpty() ||
        !user.get().isEnabled() ||
        user.get().getSecurityVersion() != principal.getSecurityVersion()
      ) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        String path = request.getRequestURI();
        if (
          path.equals("/api/auth/csrf") ||
          path.equals("/api/auth/forgot-password") ||
          path.equals("/api/auth/reset-password")
        ) {
          chain.doFilter(request, response);
          return;
        }
        response.sendError(401);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
