package com.hackIAThon.solutionback.controller;

import com.hackIAThon.solutionback.service.ingestion.TariffIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/tariff")
public class TariffUploadController {

    private final TariffIngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;

    public TariffUploadController(TariffIngestionService ingestionService, JdbcTemplate jdbcTemplate) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/list")
    public ResponseEntity<List<String>> listTariffs() {
        List<String> sources = jdbcTemplate.queryForList(
            "SELECT DISTINCT metadata->>'source' FROM vector_store " +
            "WHERE metadata->>'source' IS NOT NULL ORDER BY 1",
            String.class
        );
        return ResponseEntity.ok(sources);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTariff(@RequestPart("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            int chunks = ingestionService.ingestPdf(filename, file.getBytes());

            if (chunks == 0) {
                return ResponseEntity.ok(Map.of(
                        "filename", filename,
                        "chunksIndexed", 0,
                        "message", "Tariff PDF already indexed — no changes made."
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "filename", filename,
                    "chunksIndexed", chunks,
                    "message", "Tariff PDF indexed successfully."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to index tariff PDF: " + e.getMessage()));
        }
    }
}
