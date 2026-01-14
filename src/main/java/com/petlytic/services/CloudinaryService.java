package com.petlytic.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public String uploadUserAvatar(MultipartFile file, UUID userId) {
        validateFile(file);
        try {
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "avatars",
                            "public_id", "user_" + userId,
                            "overwrite", true,
                            "resource_type", "image",
                            "width", 500,
                            "height", 500,
                            "crop", "fill",
                            "gravity", "face"
                    )
            );

            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Upload avatar failed" + e.getMessage(), e);
        }
    }

    public String uploadProductImage(MultipartFile file, UUID productId) {
        validateFile(file);
        try {
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "products/product_" + productId,
                            "resource_type", "image"
                    )
            );

            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Upload product image failed", e);
        }
    }

    //    Helper

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (!file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }
    }

    public void deleteImage(String publicId) {
        try {
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

            String resultStatus = (String) result.get("result");
            if (!"ok".equals(resultStatus)) {
                throw new RuntimeException("Delete image failed: " + resultStatus);
            }
        } catch (IOException e) {
            throw new RuntimeException("Connection error while deleting the image: " + e.getMessage(), e);
        }
    }

    public String getPublicIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        try {
            Pattern pattern = Pattern.compile("upload/(?:v\\d+/)?([^.]+)");
            Matcher matcher = pattern.matcher(imageUrl);

            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
