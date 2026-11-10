package org.example.utils;

import org.example.dto.order.OrderCreateRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class OrderRequestUtils {

    private OrderRequestUtils() {
    }

    public static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }

    public static String createRequestHash(OrderCreateRequest request) {
        // Bir xil kalit boshqa mazmundagi buyurtma uchun ishlatilmaganini shu hash bilan tekshiramiz.
        StringBuilder requestContent = new StringBuilder();
        requestContent.append(request.companyId()).append(';');

        appendText(requestContent, normalizeText(request.deliveryAddress()));
        appendText(requestContent, normalizeText(request.note()));

        if (request.longitude() != null && request.latitude() != null) {
            requestContent.append("coordinates;");
            appendText(requestContent, request.longitude().stripTrailingZeros().toPlainString());
            appendText(requestContent, request.latitude().stripTrailingZeros().toPlainString());
        }

        List<OrderCreateRequest.Item> sortedItems = new ArrayList<>(request.items());
        sortedItems.sort(Comparator.comparing(OrderCreateRequest.Item::productId));

        for (OrderCreateRequest.Item item : sortedItems) {
            // 2 va 2.000 bir xil miqdor sifatida olinadi. Pozitsiyalar tartibi ham ta'sir qilmaydi.
            String quantity = item.quantity().stripTrailingZeros().toPlainString();
            requestContent.append(item.productId()).append(':').append(quantity).append(';');
        }

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] contentBytes = requestContent.toString().getBytes(StandardCharsets.UTF_8);
            byte[] hashBytes = sha256.digest(contentBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algoritmi topilmadi", exception);
        }
    }

    private static void appendText(StringBuilder content, String text) {
        // Uzunlikni qo'shish matn ichidagi ':' yoki ';' belgilarini xavfsiz ajratishga yordam beradi.
        int textLength = text == null ? -1 : text.length();
        content.append(textLength).append(':').append(text).append(';');
    }
}
