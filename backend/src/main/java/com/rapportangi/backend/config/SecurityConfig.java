package com.rapportangi.backend.config;

import com.rapportangi.backend.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return email -> {
      var user = users
        .findByEmailIgnoreCase(email.strip())
        .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable"));
      return new SessionAccount(user);
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    UserRepository users
  ) throws Exception {
    return http
      .addFilterAfter(
        new SessionAccountFilter(users),
        org.springframework.security.web.context.SecurityContextHolderFilter.class
      )
      .csrf(csrf ->
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
      )
      .authorizeHttpRequests(auth ->
        auth
          .requestMatchers(
            "/api/auth/csrf",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/branding/**",
            "/error"
          )
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .requestCache(cache -> cache.disable())
      .exceptionHandling(errors ->
        errors.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
      )
      .formLogin(login ->
        login
          .loginProcessingUrl("/api/auth/login")
          .usernameParameter("email")
          .successHandler((req, res, auth) -> res.setStatus(204))
          .failureHandler((req, res, ex) -> res.sendError(401))
      )
      .logout(logout ->
        logout
          .logoutUrl("/api/auth/logout")
          .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
      )
      .build();
  }
}
