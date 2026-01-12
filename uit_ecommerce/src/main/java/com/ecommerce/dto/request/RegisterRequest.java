package com.ecommerce.dto.request;

public record RegisterRequest(String fullName,
                              String email,
                              String password) {
}
