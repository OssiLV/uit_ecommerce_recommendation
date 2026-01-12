package com.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for TensorFlow Serving prediction response.
 * 
 * Response format from TF Serving:
 * {
 * "predictions": [
 * {
 * "scores": [0.95, 0.87, 0.82, ...],
 * "product_ids": ["15", "8", "23", ...]
 * }
 * ]
 * }
 */
public record TfServingResponse(
        @JsonProperty("predictions") List<Prediction> predictions) {
    public record Prediction(
            @JsonProperty("scores") List<Double> scores,
            @JsonProperty("product_ids") List<String> productIds) {
    }
}
