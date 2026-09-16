package com.example.enums;

public enum VacancyStatus {
   DRAFT,           // Vakansiya qoralama holatda, hali e'lon qilinmagan.
   ARCHIVE,         // Vakansiya arxivga o'tkazilgan, faol emas.
   UNDER_MODERATION,// Vakansiya moderator tomonidan tekshirilmoqda.
   PUBLISHED,       // Vakansiya tasdiqlangan va saytda e'lon qilingan.
   REJECTED,        // Vakansiya moderator tomonidan rad etilgan.
   CLOSED           // Vakansiya yopilgan, ariza qabul qilish to'xtatilgan.
}

