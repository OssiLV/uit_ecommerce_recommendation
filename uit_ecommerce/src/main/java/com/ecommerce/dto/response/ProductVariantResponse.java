package com.ecommerce.dto.response;

import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;

@Builder
public record ProductVariantResponse(Long id,
        String color,
        String size,
        BigDecimal price,
        Integer stockQuantity,
        String sku) implements Serializable {
}
