package com.ecommerce.service;

import com.ecommerce.dto.request.ReviewRequest;
import com.ecommerce.dto.response.ReviewResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Review;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ReviewRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Transactional
    public ReviewResponse createReview(ReviewRequest request) {
        User user = getCurrentUser();

        // 1. Kiểm tra đơn hàng
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not your order");
        }

        // 2. Kiểm tra trạng thái DELIVERED
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new RuntimeException("You can only review delivered orders");
        }

        // 3. Kiểm tra sản phẩm có trong đơn hàng không
        boolean hasProduct = order.getItems().stream()
                .anyMatch(item -> item.getProductVariant().getProduct().getId().equals(request.productId()));
        if (!hasProduct) {
            throw new RuntimeException("Product not in this order");
        }

        // 4. Kiểm tra đã review chưa
        if (reviewRepository.existsByUserIdAndProductIdAndOrderId(user.getId(), request.productId(), request.orderId())) {
            throw new RuntimeException("You already reviewed this product for this order");
        }

        Product product = productRepository.findById(request.productId()).orElseThrow();

        // 5. Lưu Review
        Review review = Review.builder()
                .user(user)
                .product(product)
                .order(order)
                .rating(request.rating())
                .comment(request.comment())
                .createdAt(LocalDateTime.now())
                .build();

        reviewRepository.save(review);

        // 6. Tính lại điểm trung bình cho Product (Update Aggregate)
        updateProductRating(product);

        return new ReviewResponse(
                review.getId(),
                user.getFullName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt().toString()
        );
    }

    // Hàm xem danh sách review
    public Page<ReviewResponse> getReviewsByProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(r -> new ReviewResponse(
                        r.getId(),
                        r.getUser().getFullName(),
                        r.getRating(),
                        r.getComment(),
                        r.getCreatedAt().toString()
                ));
    }

    private void updateProductRating(Product product) {
        // Gọi Query từ Repository thay vì getReviews()
        Object result = reviewRepository.findRatingStatByProductId(product.getId());

        // Kết quả trả về là một mảng Object[] (do query select 2 trường)
        Object[] stats = (Object[]) result;

        Double avgRating = (Double) stats[0]; // Có thể null nếu chưa có review nào
        Long reviewCount = (Long) stats[1];   // Luôn có giá trị (ít nhất là 0)

        // Xử lý null
        if (avgRating == null) {
            avgRating = 0.0;
        }

        // Làm tròn 1 chữ số thập phân (VD: 4.666 -> 4.7)
        double roundedAvg = Math.round(avgRating * 10.0) / 10.0;

        // Cập nhật vào Product
        product.setAverageRating(roundedAvg);
        product.setReviewCount(reviewCount.intValue());

        // Lưu lại Product
        productRepository.save(product);
    }
}