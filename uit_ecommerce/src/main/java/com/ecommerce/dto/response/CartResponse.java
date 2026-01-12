package com.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long id,
                           BigDecimal totalAmount,
                           Integer totalItems,
                           List<CartItemResponse> items) {
}
