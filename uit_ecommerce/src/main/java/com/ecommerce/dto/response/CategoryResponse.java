package com.ecommerce.dto.response;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record CategoryResponse(
                Long id,
                String name,
                String description) implements Serializable {
}