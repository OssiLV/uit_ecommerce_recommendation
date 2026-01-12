package com.ecommerce.entity;

import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    // Lưu địa chỉ giao hàng (Snapshot - dạng text để không bị đổi khi user sửa profile)
    private String shippingAddress;
    private String shippingPhone;
    private String receiverName;

    private LocalDateTime deliveryDate; // Ngày giao thành công
    private String cancelReason;        // Lý do hủy đơn

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;
}