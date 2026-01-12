package com.ecommerce.mapper;

import com.ecommerce.dto.response.CategoryResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.dto.response.ProductVariantResponse;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductImage;
import com.ecommerce.entity.ProductVariant;

import java.util.Collections;
import java.util.stream.Collectors;

public class ProductMapper {

    // 1. Map từ Product Entity -> ProductResponse
    public static ProductResponse toProductResponse(Product product) {
        if (product == null) return null;

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .category(toCategoryResponse(product.getCategory()))
                // Xử lý list images (tránh null)
                .images(product.getImages() == null ? Collections.emptyList()
                        : product.getImages().stream()
                        .map(ProductImage::getImageUrl)
                        .collect(Collectors.toList()))
                // Xử lý list variants (tránh null)
                .variants(product.getVariants() == null ? Collections.emptyList()
                        : product.getVariants().stream()
                        .map(ProductMapper::toVariantResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    // 2. Map từ Variant Entity -> VariantResponse
    public static ProductVariantResponse toVariantResponse(ProductVariant variant) {
        if (variant == null) return null;

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .color(variant.getColor())
                .size(variant.getSize())
                .price(variant.getPrice())
                .stockQuantity(variant.getStockQuantity())
                .build();
    }

    // 3. Map từ Category Entity -> CategoryResponse
    public static CategoryResponse toCategoryResponse(Category category) {
        if (category == null) return null;

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}