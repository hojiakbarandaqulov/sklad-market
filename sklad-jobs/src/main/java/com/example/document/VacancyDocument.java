package com.example.document;


import com.example.enums.ExperienceLevel;
import com.example.enums.VacancyStatus;
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
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "vacancy", createIndex = true)
@Setting(settingPath = "/elasticsearch/vacancy-index-settings.json")
public class VacancyDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String positionName;

    @Field(type = FieldType.Long)
    private Long companyId;

    @Field(type = FieldType.Long)
    private Long buyerId;

    @Field(
            type = FieldType.Scaled_Float,
            scalingFactor = 100
    )
    private BigDecimal price;

    @Field(type = FieldType.Text)
    private String employmentType;

    @Field(type = FieldType.Text)
    private String workSchedule;

    @Field(type = FieldType.Text)
    private String shortDescription;

    @Field(type = FieldType.Long)
    @Builder.Default
    private Long viewsCountCache = 0L;

    @Field(type = FieldType.Text)
    private String comment;

    @Field(type = FieldType.Long)
    private Long regionId;

    @Field(type = FieldType.Text)
    private String address;

    @Field(type = FieldType.Double)
    private Double lng;

    @Field(type = FieldType.Double)
    private Double lat;

    @Field(type = FieldType.Long)
    private Long vacancyId;

    @Field(type = FieldType.Keyword)
    private VacancyStatus vacancyStatus;

    @Builder.Default
    @Field(type = FieldType.Boolean)
    private Boolean deleted = false;

    @Builder.Default
    @Field(type = FieldType.Boolean)
    private Boolean showContacts = false;

    @Field(type = FieldType.Keyword)
    private ExperienceLevel experienceLevel;

    @Field(type = FieldType.Text)
    private String requirements;

    @Field(type = FieldType.Text)
    private String workingConditions;

    @Field(type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis")
    private Instant publishedAt;
}
