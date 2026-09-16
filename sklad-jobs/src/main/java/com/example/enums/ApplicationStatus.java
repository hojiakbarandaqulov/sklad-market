package com.example.enums;

public enum ApplicationStatus {
    NEW,              // Yangi ariza yuborildi.
    REVIEWED,         // Ish beruvchi arizani ko‘rib chiqdi.
    IN_COMMUNICATION, // Nomzod bilan yozishma davom etmoqda.
    INVITED,          // Nomzod suhbatga taklif qilindi.
    ACCEPTED,         // Nomzod ishga qabul qilindi.
    REJECTED,         // Ish beruvchi arizani rad etdi.
    ARCHIVED,         // Ariza arxivga o‘tkazildi.
    WITHDRAWN         // Nomzod o‘z arizasini bekor qildi.
}