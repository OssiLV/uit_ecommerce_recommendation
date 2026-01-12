package com.ecommerce.dto.response;

import java.io.Serializable;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ProductResponse(Long id,
                              String name,
                              String description,
                              BigDecimal basePrice,
                              CategoryResponse category,
                              List<String> images,       // Chỉ cần trả về List URL string cho nhẹ
                              List<ProductVariantResponse> variants) implements Serializable {
}
