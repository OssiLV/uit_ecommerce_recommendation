package com.ecommerce.service;

import com.ecommerce.dto.request.PlaceOrderRequest;
import com.ecommerce.dto.response.OrderItemResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.*;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final InteractionService interactionService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Transactional // Quan trọng: Đảm bảo tính toàn vẹn (Trừ kho, Tạo đơn, Xóa giỏ)
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        // 1. Tạo đối tượng Order
        Order order = Order.builder()
                .user(user)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING) // Mặc định là PENDING
                .paymentMethod(request.paymentMethod())
                .receiverName(request.receiverName())
                .shippingAddress(request.shippingAddress())
                .shippingPhone(request.phoneNumber())
                .totalAmount(BigDecimal.ZERO) // Sẽ tính lại bên dưới
                .build();

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 2. Duyệt qua từng món trong giỏ hàng
        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getProductVariant();

            // Check tồn kho lần cuối (Critical Check)
            if (variant.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Out of stock: " + variant.getProduct().getName());
            }

            // Trừ kho
            variant.setStockQuantity(variant.getStockQuantity() - cartItem.getQuantity());
            // (JPA sẽ tự động update variant xuống DB khi transaction kết thúc)

            // Tạo OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariant(variant)
                    .quantity(cartItem.getQuantity())
                    .price(variant.getPrice()) // Snapshot giá
                    .build();

            orderItems.add(orderItem);

            // Tính tổng tiền
            BigDecimal itemTotal = variant.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setItems(orderItems);
        order.setTotalAmount(totalAmount);

        // 3. Lưu đơn hàng
        Order savedOrder = orderRepository.save(order);

        // --- TRACKING CODE ---
        // Loop qua các item đã mua để log
        for (OrderItem item : savedOrder.getItems()) {
            Long productId = item.getProductVariant().getProduct().getId();
            interactionService.log(user.getId(), productId, "PURCHASE");
        }
        // ---------------------

        // 4. Xóa sạch giỏ hàng
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        User user = getCurrentUser();
        List<Order> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        return orders.stream().map(this::mapToOrderResponse).toList();
    }

    @Transactional
    public OrderResponse userCancelOrder(Long orderId, String reason) {
        User user = getCurrentUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Check quyền sở hữu
        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your order");
        }

        // Validate: Chỉ được hủy khi PENDING hoặc CONFIRMED (tùy chính sách shop)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("Bạn chỉ có thể hủy đơn khi đơn hàng chưa được xử lý/giao đi.");
        }

        // Logic hủy & Hoàn kho
        performCancel(order, reason);

        return mapToOrderResponse(orderRepository.save(order));
    }


    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus oldStatus = order.getStatus();

        // Không cho phép thay đổi nếu đơn đã kết thúc
        if (oldStatus == OrderStatus.CANCELLED || oldStatus == OrderStatus.DELIVERED) {
            throw new RuntimeException("Đơn hàng đã kết thúc, không thể thay đổi trạng thái.");
        }

        // STATE MACHINE: Kiểm soát luồng trạng thái
        switch (newStatus) {
            case CONFIRMED:
                if (oldStatus != OrderStatus.PENDING)
                    throw new RuntimeException("Chỉ có thể Xác nhận đơn hàng đang Chờ.");
                break;

            case SHIPPING:
                if (oldStatus != OrderStatus.CONFIRMED)
                    throw new RuntimeException("Chỉ có thể Giao hàng đơn đã Xác nhận.");
                break;

            case DELIVERED:
                if (oldStatus != OrderStatus.SHIPPING)
                    throw new RuntimeException("Chỉ có thể Hoàn tất đơn đang Giao.");
                order.setDeliveryDate(LocalDateTime.now()); // Set ngày giao
                // Nếu thanh toán COD, lúc này cập nhật paymentStatus = PAID
                break;

            case CANCELLED:
                // Admin có quyền hủy ở PENDING hoặc CONFIRMED
                performCancel(order, "Đã hủy bởi Admin");
                break;

            default:
                throw new RuntimeException("Trạng thái không hợp lệ.");
        }

        order.setStatus(newStatus);
        return mapToOrderResponse(orderRepository.save(order));
    }

    private void performCancel(Order order, String reason) {
        // 1. Hoàn lại tồn kho cho từng sản phẩm
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getProductVariant();
            variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            // JPA sẽ tự update xuống DB khi kết thúc Transaction
        }

        // 2. Cập nhật thông tin hủy
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
    }

    // Helper mapping
    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductVariant().getProduct().getId(),
                        item.getProductVariant().getProduct().getName(),
                        item.getProductVariant().getColor(),
                        item.getProductVariant().getSize(),
                        item.getQuantity(),
                        item.getPrice()
                )).toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderDate(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getShippingAddress(),
                itemResponses
        );
    }
}