package com.ecommerce.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(Long id,
                               Long productId,
                               String productName,
                               String productImage, // Ảnh đại diện
                               String color,
                               String size,
                               BigDecimal price,
                               Integer quantity,
                               BigDecimal subTotal // price * quantity
) {
}
