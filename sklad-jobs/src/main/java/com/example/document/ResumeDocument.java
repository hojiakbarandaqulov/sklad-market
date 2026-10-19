package com.example.document;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "resumes-v1", createIndex = true)
@Setting(settingPath = "/elasticsearch/vacancy-index-settings.json")
public class ResumeDocument {
    @Id
    private String id;

    // Rezyume egasi; backend JWT dan oladi.
    @Field(type = FieldType.Long)
    private Long candidateId;

    @Field(type = FieldType.Keyword, index = false)
    private String fileId;

    @Field(type = FieldType.Long)
    private Long resumeId;

    @Builder.Default
    @Field(type = FieldType.Boolean)
    private Boolean deleted = false;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    private LocalDateTime createdDate;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS")
    private LocalDateTime modifiedDate;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String title;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String fullName;

    @Field(type = FieldType.Text)
    private String phone;

    @Field(type = FieldType.Text)
    private String email;

    // Nomzod yashaydigan shahar yoki hudud identifikatori.
    @Field(type = FieldType.Long)
    private Long regionId;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Text)
    private String desiredPosition;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal expectedSalary;

    // Nomzodning o'zi haqida qisqa ma'lumoti.
    @Field(type = FieldType.Text)
    private String aboutMe;

    // Kasbiy ko'nikmalar: Java, Spring Boot, PostgreSQL va boshqalar.
    @Field(type = FieldType.Text)
    private String skills;

    // Dastlab erkin matn: ish joylari, lavozimlar, davrlar va vazifalar.
    @Field(type = FieldType.Text)
    private String workExperience;

    // Dastlab erkin matn: o'quv yurti, yo'nalish va o'qish davri.
    @Field(type = FieldType.Text)
    private String education;

    // Biladigan tillari va darajalari.
    @Field(type = FieldType.Text)
    private String languages;
}
