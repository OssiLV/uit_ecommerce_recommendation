package com.ecommerce.service;

import com.ecommerce.dto.TfServingResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ecommerce.entity.UserInteraction;
import com.ecommerce.repository.UserInteractionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int FALLBACK_PRODUCT_COUNT = 5;

    private final RestTemplate restTemplate;
    private final ProductRepository productRepository;

    private final UserInteractionRepository userInteractionRepository;

    @Value("${application.ai.service.url}")
    private String tfServingUrl; // http://tf-serving:8501/v1/models/recommender:predict

    /**
     * Get recommended products using Hybrid approach (AI + Content-based).
     */
    public List<ProductResponse> getRecommendedProducts(Long userId) {
        List<ProductResponse> aiRecommendations = new ArrayList<>();
        List<ProductResponse> contentRecommendations = new ArrayList<>();

        // 1. Get AI Recommendations (Batch)
        try {
            aiRecommendations = getAiRecommendations(userId);
        } catch (Exception e) {
            log.error("AI Recommendation failed via TF Serving: {}", e.getMessage());
        }

        // 2. Get Real-time Content-based Recommendations
        try {
            contentRecommendations = getContentBasedRecommendations(userId);
        } catch (Exception e) {
            log.error("Content-based Recommendation failed: {}", e.getMessage());
        }

        // 3. Merge Results (AI first, then Content-based)
        // Deduplicate using Set of IDs
        List<ProductResponse> finalRecommendations = new ArrayList<>();
        Set<Long> addedIds = new HashSet<>();

        // Add AI results
        for (ProductResponse p : aiRecommendations) {
            if (addedIds.add(p.id())) {
                finalRecommendations.add(p);
            }
        }

        // Add Content-based results (if not already present)
        for (ProductResponse p : contentRecommendations) {
            if (addedIds.add(p.id())) {
                finalRecommendations.add(p);
            }
        }

        // 4. Fallback if empty
        if (finalRecommendations.isEmpty()) {
            return getFallbackProducts();
        }

        return finalRecommendations;
    }

    private List<ProductResponse> getAiRecommendations(Long userId) {
        log.info("Getting recommendations for userId: {} from TensorFlow Serving", userId);

        // 1. Chuẩn bị request cho TensorFlow Serving
        Map<String, Object> requestBody = Map.of(
                "instances", List.of(String.valueOf(userId)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // 2. Gọi TensorFlow Serving with simple RestTemplate call
        TfServingResponse response = restTemplate.postForObject(
                tfServingUrl, request, TfServingResponse.class);

        if (response == null || response.predictions() == null || response.predictions().isEmpty()) {
            return new ArrayList<>();
        }

        TfServingResponse.Prediction prediction = response.predictions().get(0);
        if (prediction.productIds() == null || prediction.productIds().isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> productIds = prediction.productIds().stream()
                .map(Long::parseLong)
                .toList();

        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Product> sortedProducts = new ArrayList<>();
        for (Long id : productIds) {
            if (productMap.containsKey(id)) {
                sortedProducts.add(productMap.get(id));
            }
        }

        return sortedProducts.stream()
                .map(ProductMapper::toProductResponse)
                .toList();
    }

    private List<ProductResponse> getContentBasedRecommendations(Long userId) {
        // 1. Get recent interactions
        List<UserInteraction> interactions = userInteractionRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
        if (interactions.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Extract Product IDs directly to find Categories
        Set<Long> interactedProductIds = interactions.stream()
                .map(UserInteraction::getProductId)
                .collect(Collectors.toSet());

        List<Product> interactedProducts = productRepository.findAllById(interactedProductIds);
        if (interactedProducts.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. Find most frequent Category
        Map<com.ecommerce.entity.Category, Long> categoryCount = interactedProducts.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        if (categoryCount.isEmpty()) {
            return new ArrayList<>();
        }

        com.ecommerce.entity.Category topCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topCategory == null) {
            return new ArrayList<>();
        }

        log.info("User {} is interested in Category: {}", userId, topCategory.getName());

        // 4. Recommend similar products in that Category (excluding interacted ones)
        List<Product> similarProducts = productRepository.findTop10ByCategoryIdAndIdNotIn(
                topCategory.getId(), interactedProductIds);

        return similarProducts.stream()
                .map(ProductMapper::toProductResponse)
                .toList();
    }

    /**
     * Fallback: Return latest products when TensorFlow Serving is unavailable.
     */
    private List<ProductResponse> getFallbackProducts() {
        try {
            // Lấy 5 sản phẩm mới nhất theo ID
            List<Product> latestProducts = productRepository.findAll(
                    PageRequest.of(0, FALLBACK_PRODUCT_COUNT, Sort.by(Sort.Direction.DESC, "id"))).getContent();

            log.info("Fallback: Returning {} latest products", latestProducts.size());

            return latestProducts.stream()
                    .map(ProductMapper::toProductResponse)
                    .toList();
        } catch (Exception e) {
            log.error("Fallback also failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}