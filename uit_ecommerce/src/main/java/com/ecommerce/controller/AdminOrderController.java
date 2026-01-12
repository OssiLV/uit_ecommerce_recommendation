package com.ecommerce.controller;

import com.ecommerce.dto.request.OrderStatusUpdate;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody OrderStatusUpdate request // DTO chỉ chứa { "status": "SHIPPING" }
    ) {
        return ResponseEntity.ok(orderService.adminUpdateStatus(id, request.status()));
    }
}
