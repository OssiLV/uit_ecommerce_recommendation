package com.ecommerce.dto.request;

import com.ecommerce.enums.OrderStatus;

public record OrderStatusUpdate(OrderStatus status) {
}
