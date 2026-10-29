package com.webevaluator.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.domain.Job;
import com.webevaluator.core.storage.ArtifactUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactStorageService implements ArtifactUploader {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;
    @Value("${s3.bucket}")
    private String bucket;

    @Override
    public String uploadArtifact(String jobId, String filename, byte[] data) {
        String key = jobId + "/" + filename;
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(data));
        return getPresignedUrl(jobId, filename);
    }

    @Override
    public String getPresignedUrl(String jobId, String filename) {
        String key = jobId + "/" + filename;
        var req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build();
        return s3Presigner.presignGetObject(req).url().toString();
    }

    public String generateHtmlReport(Job job, String summaryJson, String stepsJson) {
        try {
            List<Map<String, Object>> steps = objectMapper.readValue(stepsJson, List.class);
            StringBuilder sb = new StringBuilder("<html><head><meta charset='UTF-8'></head><body><h1>Report: ").append(job.getId()).append("</h1><pre>").append(summaryJson).append("</pre><table border='1'><tr><th>#</th><th>Action</th><th>Status</th><th>URL</th></tr>");
            for (Map<String, Object> s : steps) {
                String url = (String) s.get("screenshotUrl");
                sb.append("<tr><td>").append(s.get("stepNumber")).append("</td><td>").append(s.get("action")).append("</td><td>").append(s.get("status")).append("</td><td>").append(url != null ? "<a href='" + url + "' target='_blank'>Скриншот</a>" : "-").append("</td></tr>");
            }
            sb.append("</table></body></html>");
            return uploadArtifact(job.getId().toString(), "report.html", sb.toString().getBytes());
        } catch (Exception e) {
            log.error("HTML generation failed", e);
            return null;
        }
    }
}