package com.ecommerce.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(String name,
                             String description,
                             BigDecimal basePrice,
                             Long categoryId,
                             List<String> imageUrls,
                             List<ProductVariantDto> variants) {
}
