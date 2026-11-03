package com.webevaluator.api.controller;

import com.webevaluator.reporting.service.ArtifactStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs/{jobId}")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactStorageService artifactStorage;

    // ДОБАВЛЕНО :.+ чтобы Spring не обрезал расширение файла (например, .png)
    @GetMapping("/artifacts/{filename:.+}")
    public ResponseEntity<String> getArtifactUrl(@PathVariable UUID jobId, @PathVariable String filename) {
        return ResponseEntity.ok(artifactStorage.getPresignedUrl(jobId.toString(), filename));
    }

    @GetMapping("/report.html")
    public ResponseEntity<Void> getHtmlReport(@PathVariable UUID jobId) {
        String url = artifactStorage.getPresignedUrl(jobId.toString(), "report.html");
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }
}