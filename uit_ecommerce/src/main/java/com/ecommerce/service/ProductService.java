package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.*;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private final UserRepository userRepository;
    private final ProductSearchService productSearchService;

    // Lấy User đang đăng nhập
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "products_search", allEntries = true)
    })
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // 1. Map thông tin cơ bản
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .basePrice(request.basePrice())
                .category(category)
                .build();

        // 2. Map Images
        if (request.imageUrls() != null) {
            List<ProductImage> images = request.imageUrls().stream()
                    .map(url -> ProductImage.builder().imageUrl(url).product(product).build())
                    .toList();
            product.setImages(images);
        }

        // 3. Map Variants
        if (request.variants() != null) {
            List<ProductVariant> variants = request.variants().stream()
                    .map(v -> ProductVariant.builder()
                            .color(v.color())
                            .size(v.size())
                            .price(v.price())
                            .stockQuantity(v.stockQuantity())
                            .sku(v.sku())
                            .product(product)
                            .build())
                    .toList();
            product.setVariants(variants);
        }

        Product savedProduct = productRepository.save(product);

        // 4. Index vào Elasticsearch
        try {
            productSearchService.indexProduct(savedProduct);
        } catch (Exception e) {
            log.warn("Failed to index product to Elasticsearch: {}", e.getMessage());
        }

        return ProductMapper.toProductResponse(savedProduct);
    }

    // Logic lọc sản phẩm (Search & Filter) - Fallback nếu Elasticsearch không khả
    // dụng
    @Cacheable(value = "products_page", key = "#criteria.toString() + #pageable.toString()")
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null && !criteria.keyword().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + criteria.keyword().toLowerCase() + "%"));
            }
            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), criteria.maxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        return productPage.map(ProductMapper::toProductResponse);
    }

    @Cacheable(value = "product", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return ProductMapper.toProductResponse(product);
    }
}