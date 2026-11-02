package com.example.lms.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.lms.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores profile / background pictures on Cloudinary (uses the Cloudinary bean from CloudinaryConfig).
 * Render's disk is ephemeral, so nothing is written locally.
 *
 * Safety rules:
 *  - the file type is decided from the file's magic bytes, never from the client filename/content-type
 *  - only JPEG, PNG and WEBP (no SVG, so no script-in-image risk)
 *  - the public id is random (UUID) and always inside the lms/profile folder
 *  - we only ever delete assets whose URL points into our own lms/profile folder
 */
@Service
public class ProfileImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ProfileImageStorageService.class);

    private static final String FOLDER = "lms/profile";
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    /** .../image/upload/[transformations/][v123/]lms/profile/<name>.<ext> -> group(1) = lms/profile/<name> */
    private static final Pattern OWN_ASSET = Pattern.compile(
            "^https://res\\.cloudinary\\.com/[^/]+/image/upload/(?:[^/]+/)*?(?:v\\d+/)?"
                    + "(" + Pattern.quote(FOLDER) + "/[A-Za-z0-9_-]+)\\.[A-Za-z0-9]+$");

    private final Cloudinary cloudinary;

    public ProfileImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * @param kind "avatar" or "background" - only used as a public-id prefix.
     * @return the https URL Cloudinary serves the image from (stored as-is in the users table)
     */
    public String store(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Please select an image file.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException("File size exceeds 5 MB.", HttpStatus.BAD_REQUEST);
        }

        try {
            byte[] bytes = file.getBytes();
            if (!isAllowedImage(bytes)) {
                throw new ApiException("Only JPG, PNG or WEBP images are allowed.", HttpStatus.BAD_REQUEST);
            }

            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", FOLDER,
                    "public_id", kind + "-" + UUID.randomUUID(),
                    "resource_type", "image",
                    "overwrite", false));

            Object secureUrl = result.get("secure_url");
            if (!(secureUrl instanceof String url) || url.isBlank()) {
                throw new IOException("Cloudinary returned no secure_url");
            }
            return url;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            log.error("Could not upload profile image to Cloudinary", e);
            throw new ApiException("Unable to upload the image. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Best-effort delete of a previously stored image. Ignores anything that is not ours. */
    public void delete(String url) {
        if (url == null) return;
        Matcher m = OWN_ASSET.matcher(url);
        if (!m.matches()) return;
        try {
            cloudinary.uploader().destroy(m.group(1), ObjectUtils.asMap("resource_type", "image", "invalidate", true));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete old profile image {}", m.group(1), e);
        }
    }

    private static boolean isAllowedImage(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return true; // JPEG
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return true; // PNG
        }
        return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P'; // WEBP
    }
}