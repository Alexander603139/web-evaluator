package com.webevaluator.core.scenario;

import com.webevaluator.credentials.Credential;
import com.webevaluator.core.storage.ArtifactUploader;
import com.webevaluator.core.storage.ArtifactUploader;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ScenarioContext {
    private UUID jobId;
    private String targetUrl;
    private String scenarioConfig; // JSON с параметрами
    private Credential credential;
    private ArtifactUploader artifactUploader;
}