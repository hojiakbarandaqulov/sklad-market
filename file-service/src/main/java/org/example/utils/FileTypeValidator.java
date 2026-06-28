package org.example.utils;

import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class FileTypeValidator {

    private static final Tika tika = new Tika();

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );

    public static String validateAndGetMime(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);

        try (BufferedInputStream in = new BufferedInputStream(file.getInputStream())) {
            String detectedMime = tika.detect(in);

            if (!ALLOWED.containsKey(detectedMime)) {
                throw new IllegalArgumentException("Ruxsat berilmagan fayl turi: " + detectedMime);
            }

            if (!ALLOWED.get(detectedMime).contains(ext)) {
                throw new IllegalArgumentException(
                        "Fayl extension va MIME mos emas. ext=" + ext + ", mime=" + detectedMime
                );
            }

            return detectedMime;
        }
    }

    private static String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        String cleanName = filename.toLowerCase().trim();
        int dot = cleanName.lastIndexOf('.');

        if (dot < 0 || dot == cleanName.length() - 1) {
            return "";
        }

        return cleanName.substring(dot + 1);
    }
}

