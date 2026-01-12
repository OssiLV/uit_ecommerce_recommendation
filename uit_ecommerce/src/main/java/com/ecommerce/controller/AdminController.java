package com.ecommerce.controller;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserInteraction;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserInteractionRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.scheduler.AiTrainingScheduler;
import com.ecommerce.seeder.MasterDataSeeder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserInteractionRepository userInteractionRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final MasterDataSeeder masterDataSeeder;
    private final AiTrainingScheduler aiTrainingScheduler;

    @PostMapping("/master-data")
    public ResponseEntity<String> seedMasterData(
            @RequestParam(defaultValue = "50") int userCount,
            @RequestParam(defaultValue = "100") int productCount) {
        try {
            masterDataSeeder.generateData(userCount, productCount);
            return ResponseEntity.ok("Success! Created " + userCount + " users and " + productCount + " products.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/simulate")
    public ResponseEntity<String> simulateInteractions(@RequestParam(defaultValue = "1000") int count) {
        // 1. Lấy danh sách ID của User và Product hiện có
        List<Long> userIds = userRepository.findAll().stream().map(User::getId).toList();
        List<Long> productIds = productRepository.findAll().stream().map(Product::getId).toList();

        if (userIds.isEmpty() || productIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Cần ít nhất 1 User và 1 Product để mô phỏng.");
        }

        List<UserInteraction> interactions = new ArrayList<>();
        Random random = new Random();

        // 2. Vòng lặp tạo dữ liệu giả
        for (int i = 0; i < count; i++) {
            // Random User và Product
            Long randomUserId = userIds.get(random.nextInt(userIds.size()));
            Long randomProductId = productIds.get(random.nextInt(productIds.size()));

            // Random Hành động theo tỷ lệ (Weighted Random)
            // 0-69: VIEW, 70-89: CART, 90-99: PURCHASE
            int chance = random.nextInt(100);
            String type;
            double score;

            if (chance < 70) {
                type = "VIEW";
                score = 1.0;
            } else if (chance < 90) {
                type = "CART";
                score = 3.0;
            } else {
                type = "PURCHASE";
                score = 5.0;
            }

            // Random thời gian (Trong vòng 30 ngày qua)
            long randomDays = ThreadLocalRandom.current().nextLong(30);
            long randomHours = ThreadLocalRandom.current().nextLong(24);
            LocalDateTime timestamp = LocalDateTime.now().minusDays(randomDays).minusHours(randomHours);

            // Tạo đối tượng
            UserInteraction interaction = UserInteraction.builder()
                    .userId(randomUserId)
                    .productId(randomProductId)
                    .interactionType(type)
                    .ratingValue(score)
                    .timestamp(timestamp)
                    .build();

            interactions.add(interaction);
        }

        // 3. Lưu Batch
        userInteractionRepository.saveAll(interactions);

        return ResponseEntity.ok("Đã tạo thành công " + count + " dòng dữ liệu tương tác giả.");
    }

    @GetMapping("/interactions/export")
    public void exportInteractionsToCsv(HttpServletResponse response) throws IOException {
        // 1. Cấu hình Header trả về là CSV
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"interactions.csv\"");

        // 2. Lấy dữ liệu từ DB
        List<UserInteraction> interactions = userInteractionRepository.findAllDataForTraining();

        // 3. Ghi dữ liệu vào response
        try (PrintWriter writer = response.getWriter()) {
            // Ghi dòng tiêu đề (Header Row)
            // Lưu ý: Tên cột nên viết thường, cách nhau bằng dấu phẩy
            writer.println("user_id,product_id,interaction_type,rating_value,timestamp");

            // Ghi từng dòng dữ liệu
            for (UserInteraction ui : interactions) {
                writer.println(
                        (ui.getUserId() != null ? ui.getUserId() : "") + "," +
                                ui.getProductId() + "," +
                                escapeSpecialCharacters(ui.getInteractionType()) + "," +
                                ui.getRatingValue() + "," +
                                ui.getTimestamp().toString());
            }
        }
    }

    // Helper: Xử lý trường hợp dữ liệu có chứa dấu phẩy (nếu có)
    // Mặc dù interactionType của ta đơn giản, nhưng đây là best practice
    private String escapeSpecialCharacters(String data) {
        if (data == null)
            return "";
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    // ========== AI SERVICE ENDPOINTS ==========

    /**
     * Manually trigger AI model training.
     * POST /api/v1/admin/ai/train
     */
    @PostMapping("/ai/train")
    public ResponseEntity<Map<String, Object>> triggerAiTraining() {
        Map<String, Object> result = aiTrainingScheduler.trainNow();
        boolean success = (boolean) result.getOrDefault("success", false);
        return success ? ResponseEntity.ok(result) : ResponseEntity.internalServerError().body(result);
    }

    /**
     * Check AI service health and training status.
     * GET /api/v1/admin/ai/status
     */
    @GetMapping("/ai/status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        return ResponseEntity.ok(aiTrainingScheduler.checkHealth());
    }

}