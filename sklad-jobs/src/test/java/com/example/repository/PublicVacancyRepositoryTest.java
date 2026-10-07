package com.example.repository;
import com.example.entity.Vacancy;
import com.example.dto.vacancy.VacancyFilter;
import com.example.enums.*;
import com.example.repository.specification.VacancySpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes=PublicVacancyRepositoryTest.Config.class)
class PublicVacancyRepositoryTest {
    @Configuration @EnableAutoConfiguration @EntityScan("com.example.entity")
    @EnableJpaRepositories("com.example.repository") static class Config {}
    @Autowired VacancyRepository repository;
    private Vacancy save(String name, VacancyStatus status, boolean deleted, long company, long region, String price) {
        Vacancy v=new Vacancy(); v.setPositionName(name); v.setVacancyStatus(status); v.setDeleted(deleted);
        v.setCompanyId(company); v.setRegionId(region); v.setPrice(new BigDecimal(price));
        v.setEmploymentType("FULL_TIME"); v.setWorkSchedule("FIVE_TWO");
        v.setExperienceLevel(ExperienceLevel.ONE_TO_THREE_YEARS);
        return repository.saveAndFlush(v);
    }
    @Test void excludesDraftClosedAndDeletedAndDetailCannotReadThem() {
        Vacancy published=save("Open",VacancyStatus.PUBLISHED,false,42,1,"100");
        Vacancy draft=save("Draft",VacancyStatus.DRAFT,false,42,1,"100");
        Vacancy closed=save("Closed",VacancyStatus.CLOSED,false,42,1,"100");
        Vacancy deleted=save("Deleted",VacancyStatus.PUBLISHED,true,42,1,"100");
        var items=repository.findAll(VacancySpecifications.published(new VacancyFilter()));
        assertEquals(1,items.size()); assertEquals(published.getId(),items.get(0).getId());
        for (Vacancy v : new Vacancy[]{draft,closed,deleted})
            assertTrue(repository.findByIdAndVacancyStatusAndDeletedFalse(v.getId(),VacancyStatus.PUBLISHED).isEmpty());
    }
    @Test void combinesFiltersAndSearchesCaseInsensitively() {
        Vacancy wanted=save("Java developer",VacancyStatus.PUBLISHED,false,42,1,"150");
        save("Java developer",VacancyStatus.PUBLISHED,false,43,1,"150");
        save("Java developer",VacancyStatus.PUBLISHED,false,42,2,"150");
        save("Java developer",VacancyStatus.PUBLISHED,false,42,1,"300");
        save("Python developer",VacancyStatus.PUBLISHED,false,42,1,"150");
        VacancyFilter f=new VacancyFilter(); f.setQ(" JAVA "); f.setCompanyId(42L); f.setRegionId(1L);
        f.setSalaryMin(new BigDecimal("100")); f.setSalaryMax(new BigDecimal("200"));
        f.setEmploymentType("full_time"); f.setWorkSchedule("five_two"); f.setExperienceLevel(ExperienceLevel.ONE_TO_THREE_YEARS);
        var found=repository.findAll(VacancySpecifications.published(f));
        assertEquals(1,found.size()); assertEquals(wanted.getId(),found.get(0).getId());
    }
    @Test void searchTreatsSqlWildcardsAsLiteralText() {
        Vacancy wanted=save("100%_Java!",VacancyStatus.PUBLISHED,false,42,1,"100");
        save("100XXJava",VacancyStatus.PUBLISHED,false,42,1,"100");
        VacancyFilter f=new VacancyFilter(); f.setQ("%_Java!");
        var found=repository.findAll(VacancySpecifications.published(f));
        assertEquals(1,found.size()); assertEquals(wanted.getId(),found.get(0).getId());
    }
    @Test void moderationQueueIncludesOnlyPendingNonDeletedWithPagination() {
        Vacancy first = save("Pending 1", VacancyStatus.UNDER_MODERATION, false, 42, 1, "100");
        Vacancy second = save("Pending 2", VacancyStatus.UNDER_MODERATION, false, 43, 1, "100");
        save("Deleted", VacancyStatus.UNDER_MODERATION, true, 42, 1, "100");
        save("Published", VacancyStatus.PUBLISHED, false, 42, 1, "100");
        save("Draft", VacancyStatus.DRAFT, false, 42, 1, "100");
        save("Rejected", VacancyStatus.REJECTED, false, 42, 1, "100");
        var page = repository.findAllByVacancyStatusAndDeletedFalse(VacancyStatus.UNDER_MODERATION,
                org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("id")));
        assertEquals(2, page.getTotalElements());
        assertEquals(first.getId(), page.getContent().get(0).getId());
        var next = repository.findAllByVacancyStatusAndDeletedFalse(VacancyStatus.UNDER_MODERATION,
                org.springframework.data.domain.PageRequest.of(1, 1, org.springframework.data.domain.Sort.by("id")));
        assertEquals(second.getId(), next.getContent().get(0).getId());
    }}