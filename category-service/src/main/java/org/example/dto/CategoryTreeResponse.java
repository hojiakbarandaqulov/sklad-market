package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CategoryTreeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private String iconId;
    private String iconUrl;
    private Integer sortOrder;
    private List<CategoryTreeResponse> children = new ArrayList<>();
}