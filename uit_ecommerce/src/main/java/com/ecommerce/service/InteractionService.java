package com.ecommerce.service;

import com.ecommerce.entity.UserInteraction;
import com.ecommerce.repository.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InteractionService {

    // Các hằng số điểm
    private static final double SCORE_VIEW = 1.0;
    private static final double SCORE_ADD_TO_CART = 3.0;
    private static final double SCORE_PURCHASE = 5.0;
    private final UserInteractionRepository repository;

    @Async // Chạy ở thread riêng, không block request chính
    public void log(Long userId, Long productId, String type) {
        try {
            // Logic quy đổi điểm
            double score = 0.0;
            switch (type) {
                case "VIEW" -> score = SCORE_VIEW;
                case "CART" -> score = SCORE_ADD_TO_CART;
                case "PURCHASE" -> score = SCORE_PURCHASE;
            }

            UserInteraction interaction = UserInteraction.builder()
                    .userId(userId)
                    .productId(productId)
                    .interactionType(type)
                    .ratingValue(score)
                    .timestamp(LocalDateTime.now())
                    .build();

            repository.save(interaction);

            // System.out.println("Logged interaction: " + type + " for user " + userId);
        } catch (Exception e) {
            // Log lỗi nhẹ nhàng, không throw exception làm crash luồng chính
            System.err.println("Error saving interaction: " + e.getMessage());
        }
    }
}