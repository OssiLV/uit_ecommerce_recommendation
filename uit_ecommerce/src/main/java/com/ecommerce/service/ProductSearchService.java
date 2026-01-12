package com.ecommerce.service;

import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.elasticsearch.document.ProductDocument;
import com.ecommerce.elasticsearch.repository.ProductElasticsearchRepository;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductElasticsearchRepository elasticsearchRepository;
    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Tìm kiếm sản phẩm với Elasticsearch + Redis cache
     * Flow: Redis cache check -> Elasticsearch query (nếu cache miss) -> Cache
     * result
     */
    @Cacheable(value = "products_search", key = "#criteria.toString() + '_' + #pageable.toString()")
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        log.info("Searching products with Elasticsearch: {}", criteria);

        // Build dynamic criteria query - start with match_all logic
        Criteria esCriteria = null;

        // Keyword search on name/description
        if (criteria.keyword() != null && !criteria.keyword().isEmpty()) {
            String keyword = criteria.keyword().toLowerCase();
            esCriteria = new Criteria("name").matches(keyword)
                    .or(new Criteria("description").matches(keyword));
        }

        // Category filter
        if (criteria.categoryId() != null) {
            Criteria categoryCriteria = new Criteria("categoryId").is(criteria.categoryId());
            esCriteria = (esCriteria == null) ? categoryCriteria : esCriteria.and(categoryCriteria);
        }

        // Min price filter
        if (criteria.minPrice() != null) {
            Criteria minPriceCriteria = new Criteria("basePrice").greaterThanEqual(criteria.minPrice().doubleValue());
            esCriteria = (esCriteria == null) ? minPriceCriteria : esCriteria.and(minPriceCriteria);
        }

        // Max price filter
        if (criteria.maxPrice() != null) {
            Criteria maxPriceCriteria = new Criteria("basePrice").lessThanEqual(criteria.maxPrice().doubleValue());
            esCriteria = (esCriteria == null) ? maxPriceCriteria : esCriteria.and(maxPriceCriteria);
        }

        // If no criteria, match all
        if (esCriteria == null) {
            esCriteria = new Criteria();
        }

        CriteriaQuery query = new CriteriaQuery(esCriteria, pageable);
        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        log.info("Elasticsearch returned {} hits", searchHits.getTotalHits());

        // Lấy product entities từ database để có đầy đủ thông tin (images, variants)
        List<Long> productIds = searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getId())
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Product> products = productRepository.findAllById(productIds);
        List<ProductResponse> responses = products.stream()
                .map(ProductMapper::toProductResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, searchHits.getTotalHits());
    }

    /**
     * Index một sản phẩm vào Elasticsearch
     */
    public void indexProduct(Product product) {
        log.info("Indexing product to Elasticsearch: {}", product.getId());

        ProductDocument document = ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .build();

        elasticsearchRepository.save(document);
        log.info("Product indexed successfully: {}", product.getId());
    }

    /**
     * Xóa sản phẩm khỏi Elasticsearch index
     */
    @CacheEvict(value = "products_search", allEntries = true)
    public void deleteFromIndex(Long productId) {
        log.info("Deleting product from Elasticsearch: {}", productId);
        elasticsearchRepository.deleteById(productId);
    }

    /**
     * Đồng bộ tất cả sản phẩm từ database vào Elasticsearch
     * Tự động tạo index nếu chưa tồn tại
     */
    @CacheEvict(value = "products_search", allEntries = true)
    public void reindexAll() {
        log.info("Reindexing all products to Elasticsearch");

        // Ensure index exists before reindexing
        var indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        try {
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping();
                log.info("Created Elasticsearch index: products");
            }
        } catch (Exception e) {
            log.warn("Failed to check/create index, proceeding anyway: {}", e.getMessage());
        }

        List<Product> allProducts = productRepository.findAll();
        allProducts.forEach(this::indexProduct);

        log.info("Reindexed {} products", allProducts.size());
    }
}
