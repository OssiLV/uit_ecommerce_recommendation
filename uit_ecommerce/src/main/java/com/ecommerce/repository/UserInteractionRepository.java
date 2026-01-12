package com.ecommerce.repository;

import com.ecommerce.entity.UserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {
    // API này sẽ dùng để xuất dữ liệu cho Python Service sau này
    @Query("SELECT ui FROM UserInteraction ui ORDER BY ui.timestamp DESC")
    List<UserInteraction> findAllDataForTraining();

    // Get recent interactions for Hybrid Recommendation
    List<UserInteraction> findTop10ByUserIdOrderByTimestampDesc(Long userId);
}