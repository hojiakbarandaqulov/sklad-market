package org.example.exp;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {

    private final int status;

    public OrderException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static OrderException unavailable() {
        return new OrderException(
                503,
                "Bog'liq xizmat vaqtincha ishlamayapti. Shu so'rovni qayta yuboring."
        );
    }
}
