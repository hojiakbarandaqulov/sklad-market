package com.example.dto.vacancy;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Getter @Setter
public class PublicVacancyDTO extends VacancyDTO {
    private Long id;
    private String companyName;
    private String companyLogo;
    private Long viewsCount;
    private Instant publishedAt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private VacancyContactsDTO contacts;
}