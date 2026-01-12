package com.ecommerce.dto.request;

import java.math.BigDecimal;

public record ProductVariantDto(String color,
                                String size,
                                BigDecimal price,
                                Integer stockQuantity,
                                String sku) {
}
