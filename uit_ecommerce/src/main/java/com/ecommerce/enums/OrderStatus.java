package com.ecommerce.enums;

public enum OrderStatus {
    PENDING,    // Chờ xử lý / Chờ thanh toán
    CONFIRMED,  // Đã xác nhận
    SHIPPING,   // Đang giao
    DELIVERED,  // Đã giao
    CANCELLED   // Đã hủy
}