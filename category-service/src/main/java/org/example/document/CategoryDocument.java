package org.example.document;

import lombok.*;
import org.example.entity.Category;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "categories-v2", createIndex = false)
@Setting(settingPath = "/elasticsearch/category-index-settings.json")
public class CategoryDocument {
    @Id
    private String id;
    @Field(type = FieldType.Long)
    private Long categoryId;
    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameUz;
    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameRu;
    @Field(type = FieldType.Text, analyzer = "uz_ru_translit_analyzer", searchAnalyzer = "uz_ru_translit_analyzer")
    private String nameEn;
    @Field(type = FieldType.Keyword)
    private String slug;
    @Field(type = FieldType.Integer)
    private Integer sortOrder;
    @Field(type = FieldType.Boolean)
    private Boolean isActive;
    @Field(type = FieldType.Keyword, index = false)
    private String iconId;
    @Field(type = FieldType.Keyword, index = false)
    private String iconUrl;

    public static CategoryDocument from(Category category) {
        return CategoryDocument.builder().id(category.getId().toString()).categoryId(category.getId())
                .nameUz(category.getNameUz()).nameRu(category.getNameRu()).nameEn(category.getNameEn())
                .slug(category.getSlug()).sortOrder(category.getSortOrder()).isActive(category.getIsActive())
                .iconId(category.getIconId()).iconUrl(category.getIconUrl()).build();
    }

    public Category toCategory() {
        Category category = new Category();
        category.setId(categoryId);
        category.setNameUz(nameUz);
        category.setNameRu(nameRu);
        category.setNameEn(nameEn);
        category.setSlug(slug);
        category.setSortOrder(sortOrder);
        category.setIsActive(isActive);
        category.setIconId(iconId);
        category.setIconUrl(iconUrl);
        return category;
    }
}
