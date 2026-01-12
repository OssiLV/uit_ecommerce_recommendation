package com.ecommerce.repository;

import com.ecommerce.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Lấy review của 1 sản phẩm
    Page<Review> findByProductId(Long productId, Pageable pageable);

    // Kiểm tra xem user đã review sản phẩm trong đơn hàng này chưa
    boolean existsByUserIdAndProductIdAndOrderId(Long userId, Long productId, Long orderId);

    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.product.id = :productId")
    Object findRatingStatByProductId(Long productId);
}