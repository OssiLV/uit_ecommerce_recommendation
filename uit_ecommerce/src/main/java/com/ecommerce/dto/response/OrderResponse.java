package com.ecommerce.dto.response;

import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id,
                            LocalDateTime orderDate,
                            BigDecimal totalAmount,
                            OrderStatus status,
                            PaymentMethod paymentMethod,
                            String shippingAddress,
                            List<OrderItemResponse> items) {
}
