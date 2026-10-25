package com.rapportangi.backend.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(
      Map.of(
        "message",
        "Vérifiez les champs obligatoires, leurs longueurs et les valeurs sélectionnées."
      )
    );
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> status(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(
      Map.of(
        "message",
        ex.getReason() == null ? "Requête impossible" : ex.getReason()
      )
    );
  }

  @ExceptionHandler(
    org.springframework.dao.DataIntegrityViolationException.class
  )
  public ResponseEntity<?> conflict(
    org.springframework.dao.DataIntegrityViolationException ex
  ) {
    return ResponseEntity.status(409).body(
      Map.of(
        "message",
        "Email ou code de parrainage déjà utilisé pour cette équipe."
      )
    );
  }

  @ExceptionHandler(
    org.springframework.orm.ObjectOptimisticLockingFailureException.class
  )
  public ResponseEntity<?> stale(
    org.springframework.orm.ObjectOptimisticLockingFailureException ex
  ) {
    return ResponseEntity.status(409).body(
      Map.of(
        "message",
        "Ce rapport a été modifié. Actualisez avant de réessayer."
      )
    );
  }
}
