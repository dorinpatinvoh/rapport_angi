package com.rapportangi.backend.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branding")
public class BrandingController {

  @GetMapping("/{name}")
  public ResponseEntity<org.springframework.core.io.Resource> image(
    @PathVariable String name
  ) {
    if (
      !java.util.Set.of("angi.jpeg", "azo.png", "zehami.jpeg").contains(name)
    ) return ResponseEntity.notFound().build();
    return ResponseEntity.ok()
      .contentType(
        name.endsWith("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG
      )
      .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)))
      .body(new ClassPathResource("branding/" + name));
  }
}
