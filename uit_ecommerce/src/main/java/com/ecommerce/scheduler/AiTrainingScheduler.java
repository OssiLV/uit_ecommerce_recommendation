package com.ecommerce.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Scheduler for AI model management with TensorFlow Serving.
 * Note: TensorFlow Serving doesn't support online training.
 * Training must be done offline using train_model.py script,
 * TF Serving will automatically hot-reload the new model.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "application.ai.training.enabled", havingValue = "true", matchIfMissing = false)
public class AiTrainingScheduler {

    private final RestTemplate restTemplate;

    @Value("${application.ai.service.base-url}")
    private String aiServiceBaseUrl;

    private LocalDateTime lastCheckTime;
    private String lastModelStatus;

    /**
     * Scheduled model status check - runs based on cron expression.
     * Note: Training must be done offline via Python script.
     */
    @Scheduled(cron = "${application.ai.training.cron}")
    public void scheduledStatusCheck() {
        log.info("=== Scheduled AI Model Status Check ===");
        checkHealth();
    }

    /**
     * Manual training trigger.
     * Note: TensorFlow Serving doesn't support online training.
     * Returns instructions for offline training.
     */
    public Map<String, Object> trainNow() {
        log.info("=== Manual AI Training Request ===");

        // TensorFlow Serving không hỗ trợ online training
        // Training cần làm offline bằng script Python
        return Map.of(
                "success", false,
                "message", "TensorFlow Serving không hỗ trợ online training. " +
                        "Vui lòng chạy: python train_model.py để train model. " +
                        "TF Serving sẽ tự động load model mới sau 60 giây.",
                "instructions", Map.of(
                        "step1", "cd recommender-system",
                        "step2", "python train_model.py",
                        "step3", "Model sẽ tự động được load sau 60s hoặc restart tf-serving"));
    }

    /**
     * Check TensorFlow Serving model status.
     * Uses /v1/models/recommender endpoint.
     */
    public Map<String, Object> checkHealth() {
        try {
            // TensorFlow Serving model status endpoint
            String modelStatusUrl = aiServiceBaseUrl + "/v1/models/recommender";
            log.info("Checking TF Serving model status at: {}", modelStatusUrl);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(modelStatusUrl,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                lastCheckTime = LocalDateTime.now();
                lastModelStatus = "AVAILABLE";

                Map<String, Object> body = response.getBody();
                log.info("TF Serving model status: {}", body);

                return Map.of(
                        "aiServiceStatus", "UP",
                        "aiServiceUrl", aiServiceBaseUrl,
                        "modelName", "recommender",
                        "lastCheckTime", lastCheckTime.toString(),
                        "modelStatus", lastModelStatus,
                        "details", body,
                        "note", "Training cần làm offline bằng: python train_model.py");
            } else {
                lastModelStatus = "UNAVAILABLE";
                return Map.of(
                        "aiServiceStatus", "DOWN",
                        "aiServiceUrl", aiServiceBaseUrl,
                        "error", "Model status check failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            lastModelStatus = "ERROR";
            log.error("TF Serving health check error: {}", e.getMessage());
            return Map.of(
                    "aiServiceStatus", "DOWN",
                    "aiServiceUrl", aiServiceBaseUrl,
                    "error", e.getMessage(),
                    "hint", "Đảm bảo TF Serving đang chạy và model đã được train");
        }
    }

    /**
     * Get model metadata from TensorFlow Serving.
     */
    public Map<String, Object> getModelMetadata() {
        try {
            String metadataUrl = aiServiceBaseUrl + "/v1/models/recommender/metadata";
            log.info("Getting TF Serving model metadata at: {}", metadataUrl);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(metadataUrl,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return Map.of(
                        "success", true,
                        "metadata", response.getBody());
            } else {
                return Map.of(
                        "success", false,
                        "error", "Failed to get metadata");
            }
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage());
        }
    }
}
