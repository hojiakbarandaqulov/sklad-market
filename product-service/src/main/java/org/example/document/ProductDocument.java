package org.example.document;


import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.enums.Currency;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text)
    private String shortDescription;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String phone;

    @Field(type = FieldType.Keyword)
    private String moderationStatus;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private String priceType;

    @Field(
            type = FieldType.Scaled_Float,
            scalingFactor = 100
    )
    private BigDecimal price;

    @Field(type = FieldType.Long)
    private Long companyId;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Long)
    private Long regionId;

    @Field(type = FieldType.Long)
    private Long districtId;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Boolean)
    private Boolean isPromoted;

    @Field(type = FieldType.Keyword, index = false)
    private String primaryImageUrl;

    @Field(type = FieldType.Long)
    private Long viewsCountCache;

    @Field(type = FieldType.Long)
    private Long favoritesCountCache;

    @Field(type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis")
    private LocalDateTime createdAt;
}