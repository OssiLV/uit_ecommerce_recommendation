package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_interactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;      // Người dùng
    private Long productId;   // Sản phẩm tương tác

    // Loại tương tác: VIEW (1 điểm), CART (3 điểm), PURCHASE (5 điểm)
    private String interactionType;

    // Điểm số quy đổi (Dùng để train AI)
    private Double ratingValue;

    private LocalDateTime timestamp;
}