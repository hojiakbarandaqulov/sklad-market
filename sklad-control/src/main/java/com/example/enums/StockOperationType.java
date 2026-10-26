package com.example.enums;

public enum StockOperationType {
    OPENING_BALANCE, // Ombor hisobini boshlashdagi dastlabki qoldiq.
    RECEIPT,         // Omborga mahsulot kirimi.
    SALE,            // Buyurtma jo'natilgandagi mahsulot chiqimi.
    RETURN,          // Qaytgan mahsulotni omborga qabul qilish.
    WRITE_OFF,       // Yaroqsiz yoki yo'qolgan mahsulotni hisobdan chiqarish.
    ADJUSTMENT       // Sanash natijasida haqiqiy qoldiqni to'g'rilash.
}
