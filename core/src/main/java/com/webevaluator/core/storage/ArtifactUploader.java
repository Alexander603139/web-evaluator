package com.webevaluator.core.storage;

public interface ArtifactUploader {
    String uploadArtifact(String jobId, String filename, byte[] data);

    String getPresignedUrl(String jobId, String filename);
}