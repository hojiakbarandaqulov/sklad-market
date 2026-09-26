package com.example.dto.vacancy;
import com.example.enums.ExperienceLevel;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
@Getter @Setter
public class VacancyFilter {
    @Size(max=200) private String q;
    @Positive private Long companyId;
    @Positive private Long regionId;
    @DecimalMin("0") @Digits(integer=17,fraction=2) private BigDecimal salaryMin;
    @DecimalMin("0") @Digits(integer=17,fraction=2) private BigDecimal salaryMax;
    private ExperienceLevel experienceLevel;
    @Size(max=255) private String employmentType;
    @Size(max=255) private String workSchedule;
    @Min(1) private int page=1;
    @Min(1) @Max(100) private int perPage=20;
    @AssertTrue(message="salaryMin must not exceed salaryMax")
    public boolean isSalaryRangeValid() {
        return salaryMin==null || salaryMax==null || salaryMin.compareTo(salaryMax)<=0;
    }
}