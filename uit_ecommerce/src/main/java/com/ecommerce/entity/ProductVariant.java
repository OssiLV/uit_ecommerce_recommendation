package com.ecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore // Tránh vòng lặp vô tận khi convert JSON
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private String color;
    private String size;

    private BigDecimal price; // Giá thực tế của biến thể này
    private Integer stockQuantity; // Tồn kho
    private String sku; // Mã kho (VD: AO-DO-L)
}