package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CompanyProductListResponse {
    private List<CompanyProductResponse> items;
    private Integer page;
    @JsonProperty("per_page")
    private Integer perPage;
    @JsonProperty("total_elements")
    private Long totalElements;
    @JsonProperty("total_pages")
    private Integer totalPages;
}
