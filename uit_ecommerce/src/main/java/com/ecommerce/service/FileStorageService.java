package com.ecommerce.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    // 2. Danh sách các loại file ảnh cho phép
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    // 1. Lấy đường dẫn từ file config
    @Value("${application.file.upload-dir}")
    private String uploadDir;
    private Path rootLocation;

    @PostConstruct // Chạy sau khi inject value xong
    public void init() {
        try {
            this.rootLocation = Paths.get(uploadDir);
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    public String storeFile(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file.");
            }

            // 3. Kiểm tra định dạng file (Validation)
            if (!isImageFile(file)) {
                throw new RuntimeException("Only image files are allowed! (jpg, png, gif, webp)");
            }

            // 4. Kiểm tra dung lượng (Manual Check - tùy chọn thêm)
            // if (file.getSize() > 5 * 1024 * 1024) throw ...

            // Tạo tên file ngẫu nhiên
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + fileExtension;

            Path destinationFile = this.rootLocation.resolve(newFilename)
                    .normalize().toAbsolutePath();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    // Helper: Kiểm tra file ảnh
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        // Check 1: Content Type
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            return false;
        }

        // Check 2: Extension
        if (originalFilename == null) return false;
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }
}