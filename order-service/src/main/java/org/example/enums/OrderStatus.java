package org.example.enums;

public enum OrderStatus {

    RESERVING,   // Control'da rezerv yaratilmoqda.
    RESERVED,    // Mahsulot ajratilgan, jo'natish mumkin.
    FINALIZING,  // Sotuv rejasi saqlangan, chiqim tasdiqlanmoqda.
    SHIPPING,    // Jo'natish niyati saqlangan, Control javobi kutilmoqda.
    SHIPPED,     // Ombordan chiqim tasdiqlangan.
    CANCELLING,  // Rezervni bo'shatish jarayoni ketmoqda.
    CANCELLED,   // Buyurtma bekor qilingan.
    EXPIRED,     // Rezerv muddati tugagan.
    REJECTED     // Mahsulot rezervlanmagan, buyurtma rad etilgan.
}
