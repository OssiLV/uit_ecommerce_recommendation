package com.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PythonRecommendDto(
        @JsonProperty("user_id") String userId,
        @JsonProperty("recommendations") List<String> recommendations
) {
}