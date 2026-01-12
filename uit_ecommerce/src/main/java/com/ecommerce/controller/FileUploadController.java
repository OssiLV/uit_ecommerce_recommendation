package com.ecommerce.controller;

import com.ecommerce.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    // Thay đổi: Nhận mảng files
    @PostMapping("/upload")
    public ResponseEntity<List<String>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        List<String> fileUrls = new ArrayList<>();

        Arrays.stream(files).forEach(file -> {
            String filename = fileStorageService.storeFile(file);
            String fileUrl = "http://localhost:8080/uploads/" + filename;
            fileUrls.add(fileUrl);
        });

        return ResponseEntity.ok(fileUrls);
    }
}