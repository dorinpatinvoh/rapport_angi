package com.rapportangi.backend.controller;

import com.rapportangi.backend.service.AzoProfiles;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class AzoProfileController {

  @GetMapping("/api/report-profiles")
  public List<AzoProfiles.Definition> list() {
    return AzoProfiles.ALL;
  }
}
