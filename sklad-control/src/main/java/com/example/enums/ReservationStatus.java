package com.example.enums;

public enum ReservationStatus {
    ACTIVE,    // Miqdor buyurtma uchun ajratilgan, hali jo'natilmagan.
    COMMITTED, // Buyurtma jo'natilgan va miqdor ombor qoldig'idan ayrilgan.
    RELEASED,  // Buyurtma bekor qilinib, ajratilgan miqdor bo'shatilgan.
    EXPIRED    // Rezerv muddati tugab, ajratilgan miqdor bo'shatilgan.
}
