package com.ecommerce.dto.request;

import com.ecommerce.enums.PaymentMethod;

public record PlaceOrderRequest(String receiverName,
                                String shippingAddress,
                                String phoneNumber,
                                PaymentMethod paymentMethod) {
}
