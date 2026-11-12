package org.example.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopCategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String iconUrl;
    private long productCount;
}
