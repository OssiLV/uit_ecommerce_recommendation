package com.ecommerce.dto.request;

public record AddToCartRequest(Long productVariantId,
                               Integer quantity) {
}
