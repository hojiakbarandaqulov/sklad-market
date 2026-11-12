package com.example.document;

import com.example.enums.RegionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "regions")
@Setting(settingPath = "/elasticsearch/regions-index-settings.json")
public class RegionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String code;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameUz;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameRu;

    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameEn;

    @Field(type = FieldType.Text)
    private RegionType type;

    @Field(type = FieldType.Integer)
    private Integer sortOrder = 0;

}
