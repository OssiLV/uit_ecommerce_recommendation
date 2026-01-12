package com.ecommerce.controller;

import com.ecommerce.dto.request.PlaceOrderRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Đặt hàng
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        return ResponseEntity.ok(orderService.placeOrder(request));
    }

    // Xem lịch sử mua hàng
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders() {
        return ResponseEntity.ok(orderService.getMyOrders());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        // Nếu user không nhập lý do, điền mặc định
        String finalReason = (reason == null || reason.isEmpty()) ? "Changed mind" : reason;
        return ResponseEntity.ok(orderService.userCancelOrder(id, finalReason));
    }
}