package com.example.mapper;

import com.example.document.VacancyDocument;
import com.example.entity.Vacancy;
import org.springframework.util.Assert;

public final class VacancyDocumentMapper {
    private VacancyDocumentMapper() {
    }

    public static VacancyDocument from(Vacancy vacancy) {
        Assert.notNull(vacancy, "Vacancy must not be null");
        Assert.isTrue(vacancy.getId() != null && vacancy.getId() > 0,
                "Vacancy must be saved in the database before indexing");

        return VacancyDocument.builder()
                .id(vacancy.getId().toString())
                .vacancyId(vacancy.getId())
                .positionName(vacancy.getPositionName())
                .companyId(vacancy.getCompanyId())
                .buyerId(vacancy.getBuyerId())
                .price(vacancy.getPrice())
                .employmentType(vacancy.getEmploymentType())
                .workSchedule(vacancy.getWorkSchedule())
                .shortDescription(vacancy.getShortDescription())
                .viewsCountCache(vacancy.getViewsCountCache() == null ? 0L : vacancy.getViewsCountCache())
                .vacancyStatus(vacancy.getVacancyStatus())
                .comment(vacancy.getComment())
                .regionId(vacancy.getRegionId())
                .address(vacancy.getAddress())
                .lng(toCoordinate(vacancy.getLng(), "lng"))
                .lat(toCoordinate(vacancy.getLat(), "lat"))
                .experienceLevel(vacancy.getExperienceLevel())
                .requirements(vacancy.getRequirements())
                .workingConditions(vacancy.getWorkingConditions())
                .publishedAt(vacancy.getPublishedAt())
                .deleted(Boolean.TRUE.equals(vacancy.getDeleted()))
                .showContacts(Boolean.TRUE.equals(vacancy.getShowContacts()))
                .build();
    }

    private static Double toCoordinate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double coordinate = Double.parseDouble(value.trim());
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException(fieldName + " must be a finite number");
            }
            return coordinate;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a number: " + value, exception);
        }
    }
}
