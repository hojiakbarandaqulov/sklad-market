package org.example.exp;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {

    private final int status;
    // Faqat server logi uchun; frontend javobiga qo'shilmaydi.
    private final String diagnostic;

    public OrderException(int status, String message) {
        this(status, message, null);
    }

    public OrderException(int status, String message, String diagnostic) {
        super(message);
        this.status = status;
        this.diagnostic = diagnostic;
    }

    public static OrderException unavailable(String diagnostic) {
        return new AppServiceUnavailableException(
                "order.dependency.unavailable",
                diagnostic
        );
    }
}
