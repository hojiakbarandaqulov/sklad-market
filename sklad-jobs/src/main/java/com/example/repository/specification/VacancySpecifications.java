package com.example.repository.specification;
import com.example.entity.Vacancy;
import com.example.enums.VacancyStatus;
import com.example.dto.vacancy.VacancyFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
public final class VacancySpecifications {
    private VacancySpecifications() {}
    public static Specification<Vacancy> published(VacancyFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.equal(root.get("vacancyStatus"), VacancyStatus.PUBLISHED));
            if (f.getCompanyId()!=null) predicates.add(cb.equal(root.get("companyId"),f.getCompanyId()));
            if (f.getRegionId()!=null) predicates.add(cb.equal(root.get("regionId"),f.getRegionId()));
            if (f.getSalaryMin()!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("price"),f.getSalaryMin()));
            if (f.getSalaryMax()!=null) predicates.add(cb.lessThanOrEqualTo(root.get("price"),f.getSalaryMax()));
            if (f.getExperienceLevel()!=null) predicates.add(cb.equal(root.get("experienceLevel"),f.getExperienceLevel()));
            if (f.getEmploymentType()!=null && !f.getEmploymentType().isBlank())
                predicates.add(cb.equal(cb.lower(root.get("employmentType")), f.getEmploymentType().trim().toLowerCase(Locale.ROOT)));
            if (f.getWorkSchedule()!=null && !f.getWorkSchedule().isBlank())
                predicates.add(cb.equal(cb.lower(root.get("workSchedule")), f.getWorkSchedule().trim().toLowerCase(Locale.ROOT)));
            if (f.getQ()!=null && !f.getQ().isBlank()) {
                String text=f.getQ().trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
                String pattern="%"+text+"%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("positionName")),pattern,'!'),
                    cb.like(cb.lower(root.get("shortDescription")),pattern,'!'),
                    cb.like(cb.lower(root.get("responsibilities")),pattern,'!'),
                    cb.like(cb.lower(root.get("requirements")),pattern,'!')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}