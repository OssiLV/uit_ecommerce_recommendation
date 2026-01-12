package com.ecommerce.service;

import com.ecommerce.dto.request.AddToCartRequest;
import com.ecommerce.dto.response.CartItemResponse;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.entity.User;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductVariantRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final InteractionService interactionService;

    // Lấy User đang đăng nhập
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = getCurrentUser();


        // 1. Kiểm tra sản phẩm có tồn tại và còn hàng không
        ProductVariant variant = variantRepository.findById(request.productVariantId())
                .orElseThrow(() -> new RuntimeException("Product Variant not found"));

        if (variant.getStockQuantity() < request.quantity()) {
            throw new RuntimeException("Not enough stock. Available: " + variant.getStockQuantity());
        }

        // --- TRACKING CODE ---
        // Lấy ID sản phẩm gốc từ ProductVariant
        Long productId = variant.getProduct().getId();

        interactionService.log(user.getId(), productId, "CART");
        // ---------------------

        // 2. Lấy hoặc tạo giỏ hàng
        Cart cart = cartRepository.findByUser(user).orElseGet(() -> {
            Cart newCart = Cart.builder().user(user).items(new ArrayList<>()).build();
            return cartRepository.save(newCart);
        });

        // 3. Kiểm tra xem sản phẩm này đã có trong giỏ chưa
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductVariant().getId().equals(variant.getId()))
                .findFirst();

        if (existingItem.isPresent()) {
            // Cộng dồn số lượng
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.quantity());
            // Check lại stock sau khi cộng
            if (item.getQuantity() > variant.getStockQuantity()) {
                throw new RuntimeException("Max stock exceeded");
            }
        } else {
            // Thêm mới
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productVariant(variant)
                    .quantity(request.quantity())
                    .build();
            cart.getItems().add(newItem);
            // Lưu item mới (Cascade của Cart sẽ tự xử lý, nhưng save tay cho chắc nếu cần)
            cartItemRepository.save(newItem);
        }

        return getCart(); // Trả về giỏ hàng mới nhất
    }

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUser(user)
                .orElse(new Cart()); // Trả về giỏ rỗng nếu chưa có

        if (cart.getItems() == null) return new CartResponse(cart.getId(), BigDecimal.ZERO, 0, new ArrayList<>());

        var itemsDto = cart.getItems().stream().map(item -> {
            var product = item.getProductVariant().getProduct();
            // Lấy ảnh đầu tiên làm ảnh đại diện
            String img = product.getImages().isEmpty() ? "" : product.getImages().get(0).getImageUrl();

            return new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    img,
                    item.getProductVariant().getColor(),
                    item.getProductVariant().getSize(),
                    item.getProductVariant().getPrice(),
                    item.getQuantity(),
                    item.getProductVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }).toList();

        return new CartResponse(
                cart.getId(),
                cart.getTotalAmount(),
                cart.getItems().size(),
                itemsDto
        );
    }

    @Transactional
    public void removeFromCart(Long cartItemId) {
        // Cần check xem item đó có thuộc về user đang đăng nhập không (Bảo mật)
        User user = getCurrentUser();
        Cart cart = cartRepository.findByUser(user).orElseThrow();

        cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
        cartItemRepository.deleteById(cartItemId);
    }
}