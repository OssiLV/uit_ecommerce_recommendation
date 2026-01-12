package com.ecommerce.service;

import com.ecommerce.dto.request.ProductSearchCriteria;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.elasticsearch.document.ProductDocument;
import com.ecommerce.elasticsearch.repository.ProductElasticsearchRepository;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.ProductRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

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

        // 1. Build Bool Query for filtering and matching

        Query boolQuery = Query.of(q -> q.bool(b -> {

            // A. Keyword Search with Boosting (Name^3, Description^1)
            if (criteria.keyword() != null && !criteria.keyword().isEmpty()) {
                b.must(m -> m.multiMatch(mm -> mm
                        .query(criteria.keyword())
                        .fields(List.of("name^3", "description")) // Boost name 3x
                        .fuzziness("AUTO") // Optional: Fuzzy search
                ));
            } else {
                b.must(m -> m.matchAll(ma -> ma));
            }

            // B. Filters
            if (criteria.categoryId() != null) {
                b.filter(f -> f.term(t -> t.field("categoryId").value(criteria.categoryId())));
            }
            if (criteria.minPrice() != null) {
                b.filter(f -> f.range(r -> r.number(n -> n.field("basePrice")
                        .gte(criteria.minPrice().doubleValue()))));
            }
            if (criteria.maxPrice() != null) {
                b.filter(f -> f.range(r -> r.number(n -> n.field("basePrice")
                        .lte(criteria.maxPrice().doubleValue()))));
            }
            return b;
        }));

        // 2. Execute Query
        var query = new NativeQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        log.info("Elasticsearch returned {} hits", searchHits.getTotalHits());

        List<Long> productIds = searchHits.getSearchHits().stream()
                .map(hit -> hit.getContent().getId())
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Fetch full entities (preserve order from Elasticsearch hits)
        // Note: findAllById does not guarantee order, so we might need to re-sort or
        // map manually if strict order needed.
        // For now, strict order preservation logic:
        List<Product> products = productRepository.findAllById(productIds);

        // Sort products list based on productIds order (relevance order)
        java.util.Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<ProductResponse> responses = productIds.stream()
                .filter(productMap::containsKey)
                .map(productMap::get)
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
