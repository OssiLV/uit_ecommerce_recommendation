package com.ecommerce.dto.request;

public record ReviewRequest(
        Long productId,
        Long orderId,
        Integer rating,
        String comment
) {
}