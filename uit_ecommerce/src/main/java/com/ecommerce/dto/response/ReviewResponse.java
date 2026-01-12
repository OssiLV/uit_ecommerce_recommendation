package com.ecommerce.dto.response;

public record ReviewResponse(
        Long id,
        String userName,
        Integer rating,
        String comment,
        String createdAt
) {
}