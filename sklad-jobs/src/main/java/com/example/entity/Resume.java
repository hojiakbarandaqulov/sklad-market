package com.example.entity;

import com.example.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "resume", indexes = {
        @Index(name = "idx_resume_candidate", columnList = "candidate_id")
})
public class Resume extends BaseEntity {

    // Rezyume egasi; backend JWT dan oladi.
    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    // Rezyume nomi: masalan, "Java dasturchi".
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false)
    private String email;

    // Nomzod yashaydigan shahar yoki hudud identifikatori.
    @Column(nullable = false)
    private Long regionId;

    // Nomzod ishlamoqchi bo'lgan lavozim.
    private String desiredPosition;

    @Column(precision = 19, scale = 2)
    private BigDecimal expectedSalary;

    // Nomzodning o'zi haqida qisqa ma'lumoti.
    @Column(columnDefinition = "TEXT")
    private String aboutMe;

    // Kasbiy ko'nikmalar: Java, Spring Boot, PostgreSQL va boshqalar.
    @Column(columnDefinition = "TEXT")
    private String skills;

    // Dastlab erkin matn: ish joylari, lavozimlar, davrlar va vazifalar.
    @Column(columnDefinition = "TEXT")
    private String workExperience;

    // Dastlab erkin matn: o'quv yurti, yo'nalish va o'qish davri.
    @Column(columnDefinition = "TEXT")
    private String education;

    // Biladigan tillari va darajalari.
    @Column(columnDefinition = "TEXT")
    private String languages;
}