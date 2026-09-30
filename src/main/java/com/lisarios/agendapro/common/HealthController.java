package com.lisarios.agendapro.common;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
public class HealthController {
    private final Db db;
    public HealthController(Db db) { this.db=db; }
    @GetMapping("/api/health") public ResponseEntity<?> health() {
        try { db.jdbc.queryForObject("SELECT 1",Integer.class); return ResponseEntity.ok(java.util.Map.of("application","AgendaPro","status","UP","database","UP")); }
        catch(Exception e) { return ResponseEntity.status(503).body(java.util.Map.of("status","DOWN","database","DOWN")); }
    }
}
