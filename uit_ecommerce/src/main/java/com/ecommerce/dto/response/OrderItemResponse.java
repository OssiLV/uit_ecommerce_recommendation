package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId,
                                String productName,
                                String color,
                                String size,
                                Integer quantity,
                                BigDecimal price) {
}
