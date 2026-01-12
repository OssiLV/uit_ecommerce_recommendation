package com.ecommerce.controller;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.User;
import com.ecommerce.service.InteractionService;
import com.ecommerce.service.ProductSearchService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final RecommendationService recommendationService;
    private final InteractionService interactionService;

    // API Public: Tìm kiếm với Elasticsearch + Redis cache
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "true") boolean useElasticsearch) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId, minPrice, maxPrice);
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Sử dụng Elasticsearch nếu được yêu cầu, fallback về JPA nếu ES không khả dụng
        if (useElasticsearch) {
            try {
                return ResponseEntity.ok(productSearchService.search(criteria, pageable));
            } catch (Exception e) {
                log.warn("Elasticsearch search failed, falling back to JPA: {}", e.getMessage());
                return ResponseEntity.ok(productService.searchProducts(criteria, pageable));
            }
        }

        return ResponseEntity.ok(productService.searchProducts(criteria, pageable));
    }

    // API Public: Xem chi tiết
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductDetail(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);

        // Auto-log VIEW interaction if user is authenticated
        try {
            User currentUser = productService.getCurrentUser();
            if (currentUser != null) {
                interactionService.log(currentUser.getId(), id, "VIEW");
            }
        } catch (Exception e) {
            // Ignore if user is anonymous or not found
        }

        return ResponseEntity.ok(product);
    }

    // API Admin: Tạo sản phẩm
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(request));
    }

    // API Admin: Reindex tất cả sản phẩm vào Elasticsearch
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reindexProducts() {
        productSearchService.reindexAll();
        return ResponseEntity.ok("Products reindexed successfully");
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<ProductResponse>> getRecommendations() {
        User currentUser = productService.getCurrentUser();
        return ResponseEntity.ok(recommendationService.getRecommendedProducts(currentUser.getId()));
    }
}