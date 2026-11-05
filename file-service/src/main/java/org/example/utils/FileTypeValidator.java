package org.example.utils;

import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileTypeValidator {

    private static final Tika tika = new Tika();

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "application/pdf", Set.of("pdf"),
            "application/msword", Set.of("doc"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")
    );

    public static String validateAndGetMime(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);

        try (BufferedInputStream in = new BufferedInputStream(file.getInputStream())) {
            String detectedMime = tika.detect(in, originalName);

            // Tika core recognises ZIP/OLE containers. Check the Word package before
            // accepting a DOCX, so an arbitrary ZIP renamed to .docx is rejected.
            if (ext.equals("docx")) {
                validateWordPackage(file);
                detectedMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

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

    private static void validateWordPackage(MultipartFile file) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 1000) throw new IllegalArgumentException("DOCX tarkibi juda katta");
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                document |= entry.getName().equals("word/document.xml");
                if (contentTypes && document) return;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > 16 * 1024 * 1024) {
                        throw new IllegalArgumentException("DOCX tarkibi juda katta");
                    }
                }
            }
        }
        throw new IllegalArgumentException("Yaroqsiz DOCX fayli");
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

