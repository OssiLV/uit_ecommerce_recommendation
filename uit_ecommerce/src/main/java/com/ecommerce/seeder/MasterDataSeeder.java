package com.ecommerce.seeder;

import com.ecommerce.entity.*;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.Role;
import com.ecommerce.repository.*;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MasterDataSeeder {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final UserInteractionRepository userInteractionRepository;
    private final PasswordEncoder passwordEncoder;

    private final Faker faker = new Faker(new Locale("vi"));
    private final Random random = new Random();

    @Bean
    public CommandLineRunner seedData() {
        return args -> {
            if (userRepository.count() == 0) {
                log.info(">>> Starting data seeding...");
                seedMasterData();
                log.info(">>> Data seeding completed!");
            } else {
                log.info(">>> Data already exists, skipping seeding.");
            }
        };
    }

    @Transactional
    public void seedMasterData() {
        // 1. Create Users (Admin & User1)
        List<User> users = createUsers();

        // 2. Create Categories
        List<Category> categories = createCategories();

        // 3. Create Products with Images & Variants
        List<Product> products = createProducts(categories, 50);

        // 4. Create Carts for users
        createCarts(users);

        // 5. Create sample Orders
        createOrders(users, products);

        // 6. Create sample Reviews
        createReviews(users, products);

        // 7. Create User Interactions for ML Training
        createUserInteractions(users, products, 1000);
    }

    /**
     * Generate data with custom counts - for AdminController API
     */
    @Transactional
    public void generateData(int userCount, int productCount) {
        log.info(">>> Generating {} users and {} products...", userCount, productCount);

        // Create additional users
        for (int i = 0; i < userCount; i++) {
            String email = faker.internet().emailAddress();
            if (!userRepository.existsByEmail(email)) {
                User user = User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("123456"))
                        .fullName(faker.name().fullName())
                        .role(Role.USER)
                        .build();
                userRepository.save(user);
            }
        }

        // Create categories if not exist
        List<Category> categories = createCategories();

        // Create products
        createProducts(categories, productCount);

        log.info(">>> Generated data successfully!");
    }

    private List<User> createUsers() {
        List<User> users = new ArrayList<>();

        // Admin user
        if (!userRepository.existsByEmail("admin@mail.com")) {
            User admin = User.builder()
                    .email("admin@mail.com")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Administrator")
                    .role(Role.ADMIN)
                    .build();
            users.add(userRepository.save(admin));
            log.info(">>> Created admin user: admin@mail.com / 123456");
        }

        // Additional test users
        for (int i = 1; i <= 10; i++) {
            String email = "user" + i + "@mail.com";
            if (!userRepository.existsByEmail(email)) {
                User user = User.builder()
                        .email(email)
                        .password(passwordEncoder.encode("123456"))
                        .fullName(faker.name().fullName())
                        .role(Role.USER)
                        .build();
                users.add(userRepository.save(user));
            }
        }

        log.info(">>> Created {} users", users.size());
        return users.isEmpty() ? userRepository.findAll() : users;
    }

    private List<Category> createCategories() {
        if (categoryRepository.count() > 0) {
            return categoryRepository.findAll();
        }

        String[][] categoryData = {
                { "Điện thoại", "Điện thoại thông minh các hãng" },
                { "Laptop", "Laptop văn phòng và gaming" },
                { "Thời trang nam", "Quần áo và phụ kiện nam" },
                { "Thời trang nữ", "Quần áo và phụ kiện nữ" },
                { "Giày dép", "Giày thể thao, giày da, dép" },
                { "Đồng hồ", "Đồng hồ thông minh và cơ" },
                { "Sách", "Sách văn học, kinh tế, kỹ năng" },
                { "Đồ gia dụng", "Thiết bị gia đình" },
                { "Mỹ phẩm", "Chăm sóc da và trang điểm" },
                { "Thể thao", "Dụng cụ thể thao và outdoor" }
        };

        List<Category> categories = new ArrayList<>();
        for (String[] data : categoryData) {
            Category cat = Category.builder()
                    .name(data[0])
                    .description(data[1])
                    .build();
            categories.add(categoryRepository.save(cat));
        }

        log.info(">>> Created {} categories", categories.size());
        return categories;
    }

    private List<Product> createProducts(List<Category> categories, int count) {
        if (productRepository.count() > 0) {
            return productRepository.findAll(); // Trả về nếu đã có data
        }

        List<Product> products = new ArrayList<>();
        String[] sizes = { "S", "M", "L", "XL" };
        String[] colors = { "Đen", "Trắng", "Xanh", "Vàng", "Đỏ", "Bạc" };

        // Map category -> list sản phẩm thực tế
        java.util.Map<String, String[]> productMap = new java.util.HashMap<>();
        productMap.put("Điện thoại", new String[] {
                "iPhone 15 Pro Max", "Samsung Galaxy S24 Ultra", "Xiaomi 14", "Google Pixel 8 Pro",
                "OPPO Find X7", "Vivo X100 Pro", "Asus ROG Phone 8", "Sony Xperia 1 V", "OnePlus 12", "iPhone 14 Plus"
        });
        productMap.put("Laptop", new String[] {
                "MacBook Air M2", "Dell XPS 13 Plus", "ASUS ROG Strix G16", "Lenovo ThinkPad X1 Carbon",
                "HP Spectre x360", "Acer Swift Go 14", "MSI Raider GE78", "Surface Laptop Studio 2", "LG Gram 17"
        });
        productMap.put("Thời trang nam", new String[] {
                "Áo thun nam Basic", "Quần Jeans nam Slimfit", "Áo sơ mi nam công sở", "Áo khoác Bomber nam",
                "Quần short nam Kaki", "Áo Polo nam Classic", "Vest nam lịch lãm", "Áo Hoodie nam Unisex"
        });
        productMap.put("Thời trang nữ", new String[] {
                "Đầm dự tiệc sang trọng", "Chân váy xếp ly", "Áo croptop năng động", "Quần ống rộng Culottes",
                "Áo dài cách tân", "Đầm maxi đi biển", "Áo blazer nữ", "Set đồ bộ thể thao nữ"
        });
        productMap.put("Giày dép", new String[] {
                "Giày Nike Air Force 1", "Giày Adidas Ultraboost", "Giày Biti's Hunter", "Giày Converse Chuck Taylor",
                "Giày da nam Oxford", "Giày cao gót nữ 7cm", "Sandal nam da bò", "Dép Crocs Classic"
        });
        productMap.put("Đồng hồ", new String[] {
                "Apple Watch Series 9", "Samsung Galaxy Watch 6", "Đồng hồ Casio G-Shock", "Đồng hồ Orient Bambino",
                "Garmin Fenix 7", "Seiko 5 Sport", "Hublot Big Bang (Replica)", "Daniel Wellington Classic"
        });
        productMap.put("Sách", new String[] {
                "Nhà Giả Kim", "Đắc Nhân Tâm", "Tuổi Trẻ Đáng Giá Bao Nhiêu", "Cà Phê Cùng Tony",
                "Lược Sử Loài Người", "Cha Giàu Cha Nghèo", "Tâm Lý Học Tội Phạm", "Tôi Thấy Hoa Vàng Trên Cỏ Xanh"
        });
        productMap.put("Đồ gia dụng", new String[] {
                "Nồi chiên không dầu Philips", "Robot hút bụi Xiaomi", "Máy lọc không khí Sharp",
                "Bàn ủi hơi nước cầm tay",
                "Máy xay sinh tố Sunhouse", "Bếp từ đôi Kangaroo", "Lò vi sóng Electrolux"
        });
        productMap.put("Mỹ phẩm", new String[] {
                "Son MAC Ruby Woo", "Kem chống nắng La Roche-Posay", "Nước tẩy trang Bioderma",
                "Serum Vitamin C Klairs",
                "Sữa rửa mặt CeraVe", "Phấn nước Laneige Neo Cushion", "Son dưỡng Dior Lip Glow"
        });
        productMap.put("Thể thao", new String[] {
                "Vợt cầu lông Yonex", "Thảm tập Yoga định tuyến", "Bóng đá số 5 Động Lực",
                "Giày đá bóng sân cỏ nhân tạo",
                "Dây nhảy thể lực", "Găng tay thủ môn", "Tạ tay bọc nhựa 5kg"
        });

        for (Category cat : categories) {
            String[] realProducts = productMap.getOrDefault(cat.getName(),
                    new String[] { cat.getName() + " Mẫu 1", cat.getName() + " Mẫu 2" });

            for (String prodName : realProducts) {
                double priceVal = 100000 + (30000000 - 100000) * random.nextDouble();
                if (cat.getName().equals("Điện thoại") || cat.getName().equals("Laptop"))
                    priceVal += 10000000; // Đồ điện tử đắt hơn

                BigDecimal price = BigDecimal.valueOf((long) priceVal);

                Product product = Product.builder()
                        .name(prodName) // Tên thật
                        .description("Mô tả chi tiết cho sản phẩm " + prodName
                                + ". Chất lượng cao, chính hãng, bảo hành 12 tháng. " + faker.lorem().paragraph(2))
                        .basePrice(price)
                        .category(cat)
                        .averageRating(3.5 + random.nextDouble() * 1.5) // Rating cao 3.5 - 5.0
                        .reviewCount(10 + random.nextInt(500))
                        .build();

                // Images - Random từ Picsum nhưng cố định theo tên để consistent khi re-run
                List<ProductImage> images = new ArrayList<>();
                int seed = prodName.hashCode();
                for (int j = 0; j < 3; j++) {
                    ProductImage img = ProductImage.builder()
                            .imageUrl("https://picsum.photos/seed/" + (seed + j) + "/400/400")
                            .product(product)
                            .build();
                    images.add(img);
                }
                product.setImages(images);

                // Variants
                List<ProductVariant> variants = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    ProductVariant variant = ProductVariant.builder()
                            .product(product)
                            .color(colors[random.nextInt(colors.length)])
                            .size(sizes[random.nextInt(sizes.length)])
                            .price(price.add(BigDecimal.valueOf(random.nextInt(100000))))
                            .stockQuantity(random.nextInt(50))
                            .sku("SKU-" + Math.abs(seed + j))
                            .build();
                    variants.add(variant);
                }
                product.setVariants(variants);

                products.add(productRepository.save(product));
            }
        }

        log.info(">>> Created {} realistic products", products.size());
        return products;
    }

    private void createCarts(List<User> users) {
        for (User user : users) {
            if (cartRepository.findByUserId(user.getId()).isEmpty()) {
                Cart cart = Cart.builder()
                        .user(user)
                        .items(new ArrayList<>())
                        .build();
                cartRepository.save(cart);
            }
        }
        log.info(">>> Created carts for users");
    }

    private void createOrders(List<User> users, List<Product> products) {
        if (orderRepository.count() > 0)
            return;

        List<Order> orders = new ArrayList<>();
        OrderStatus[] statuses = OrderStatus.values();
        PaymentMethod[] paymentMethods = PaymentMethod.values();

        for (int i = 0; i < 20; i++) { // Increase to 20 orders
            User randomUser = users.get(random.nextInt(users.size()));
            Order order = Order.builder()
                    .user(randomUser)
                    .orderDate(LocalDateTime.now().minusDays(random.nextInt(30)))
                    .status(statuses[random.nextInt(statuses.length)])
                    .paymentMethod(paymentMethods[random.nextInt(paymentMethods.length)])
                    .shippingAddress(faker.address().fullAddress())
                    .shippingPhone("09" + faker.number().digits(8))
                    .receiverName(randomUser.getFullName())
                    .build();

            List<OrderItem> items = new ArrayList<>();
            int itemCount = 1 + random.nextInt(3);
            BigDecimal total = BigDecimal.ZERO;

            for (int j = 0; j < itemCount; j++) {
                Product randomProduct = products.get(random.nextInt(products.size()));
                if (randomProduct.getVariants() != null && !randomProduct.getVariants().isEmpty()) {
                    ProductVariant variant = randomProduct.getVariants().get(0);
                    int qty = 1 + random.nextInt(2);
                    OrderItem item = OrderItem.builder()
                            .order(order)
                            .productVariant(variant)
                            .quantity(qty)
                            .price(variant.getPrice())
                            .build();
                    items.add(item);
                    total = total.add(variant.getPrice().multiply(BigDecimal.valueOf(qty)));
                }
            }
            order.setItems(items);
            order.setTotalAmount(total);
            orders.add(orderRepository.save(order));
        }
        log.info(">>> Created {} sample orders", orders.size());
    }

    private void createReviews(List<User> users, List<Product> products) {
        if (reviewRepository.count() > 0)
            return;

        List<Review> reviews = new ArrayList<>();
        String[] positiveReviews = {
                "Sản phẩm tuyệt vời, đúng như mong đợi!", "Giao hàng siêu nhanh, đóng gói đẹp.",
                "Chất lượng rất tốt, sẽ ủng hộ shop tiếp.", "Rất đáng tiền, mọi người nên mua.",
                "Hàng chính hãng, check code ok."
        };
        String[] neutralReviews = {
                "Sản phẩm tạm ổn so với giá.", "Giao hàng hơi chậm nhưng hàng ok.",
                "Màu sắc bên ngoài hơi khác ảnh một chút.", "Dùng cũng được, không quá đặc sắc."
        };

        for (int i = 0; i < 50; i++) { // Increase to 50 reviews
            User randomUser = users.get(random.nextInt(users.size()));
            Product randomProduct = products.get(random.nextInt(products.size()));

            int rating = 3 + random.nextInt(3); // 3, 4, 5
            String comment = (rating >= 4)
                    ? positiveReviews[random.nextInt(positiveReviews.length)]
                    : neutralReviews[random.nextInt(neutralReviews.length)];

            Review review = Review.builder()
                    .user(randomUser)
                    .product(randomProduct)
                    .rating(rating)
                    .comment(comment)
                    .createdAt(LocalDateTime.now().minusDays(random.nextInt(60)))
                    .build();
            reviews.add(reviewRepository.save(review));
        }
        log.info(">>> Created {} sample reviews", reviews.size());
    }

    /**
     * Tạo UserInteraction data cho ML Training
     * Pattern: VIEW (60%), CART (25%), PURCHASE (15%)
     * Features implemented:
     * 1. Trending Products: Một số sản phẩm hot có nhiều tương tác từ mọi user.
     * 2. Category Preference: User thích category nào sẽ tương tác nhiều với sản
     * phẩm category đó.
     * 3. Cross-selling: Mua sản phẩm A (Category X) thường xem/mua kèm sản phẩm B
     * (Category Y).
     */
    private void createUserInteractions(List<User> users, List<Product> products, int count) {
        if (userInteractionRepository.count() > 0)
            return;

        List<UserInteraction> interactions = new ArrayList<>();

        // 1. Identify "Trending Products" (Top 5 products randomly chosen)
        List<Product> trendingProducts = new ArrayList<>();
        for (int k = 0; k < 5; k++)
            trendingProducts.add(products.get(random.nextInt(products.size())));
        log.info(">>> Trending products selected: {}", trendingProducts.stream().map(Product::getName).toList());

        for (User user : users) {
            // 2. Assign Category Preference for each user (1 main category preference)
            String[] categoryNames = { "Điện thoại", "Laptop", "Thời trang nam", "Thời trang nữ", "Giày dép", "Sách" };
            String preferredCatName = categoryNames[random.nextInt(categoryNames.length)];

            // Filter products by preferred category
            List<Product> preferredCatProducts = products.stream()
                    .filter(p -> p.getCategory().getName().equals(preferredCatName))
                    .toList();
            if (preferredCatProducts.isEmpty())
                preferredCatProducts = products; // fallback

            // User interactions loop
            int interactionsPerUser = 20 + random.nextInt(30); // 20-50 interactions

            for (int i = 0; i < interactionsPerUser; i++) {
                Product product;
                int rand = random.nextInt(100);

                // Decision tree for product selection:
                // 20% chance: Trending product (Global popularity)
                // 50% chance: Preferred category product (Personalization)
                // 30% chance: Random product (Discovery)

                if (rand < 20) {
                    product = trendingProducts.get(random.nextInt(trendingProducts.size()));
                } else if (rand < 70) {
                    product = preferredCatProducts.get(random.nextInt(preferredCatProducts.size()));
                } else {
                    product = products.get(random.nextInt(products.size()));
                }

                // Generate Interaction Type & Score
                int typeChance = random.nextInt(100);
                String type;
                double score;

                if (typeChance < 60) {
                    type = "VIEW";
                    score = 1.0;
                } else if (typeChance < 85) {
                    type = "CART";
                    score = 3.0;
                } else {
                    type = "PURCHASE";
                    score = 5.0;
                }

                interactions.add(createInteraction(user, product, type, score));

                // 3. Cross-selling Simulation (Mua cái này -> gợi ý cái kia)
                // Logic: Nếu Mua/Cart Laptop -> View/Cart Balo/Chuột (phụ kiện)
                if ((type.equals("PURCHASE") || type.equals("CART"))
                        && product.getCategory().getName().equals("Laptop")) {
                    // Find accessory (simplify: random item from category 'Đồ gia dụng' or 'Sách'
                    // as accessory substitute for demo)
                    Product accessory = products.get(random.nextInt(products.size()));
                    interactions.add(createInteraction(user, accessory, "VIEW", 1.0));
                }
            }
        }

        userInteractionRepository.saveAll(interactions);
        log.info(">>> Created {} smart user interactions for ML training", interactions.size());
    }

    private UserInteraction createInteraction(User user, Product product, String type, double score) {
        return UserInteraction.builder()
                .userId(user.getId())
                .productId(product.getId())
                .interactionType(type)
                .ratingValue(score)
                .timestamp(LocalDateTime.now()
                        .minusDays(random.nextInt(30))
                        .minusHours(random.nextInt(24))
                        .minusMinutes(random.nextInt(60)))
                .build();
    }
}