package com.example.service;

import com.example.entity.Vacancy;
import java.util.List;

public interface JobsVacancyAccess {
    long viewer();

    List<Long> ownedCompanies(long viewer);

    Vacancy owned(Long id, boolean lock, boolean published);

    boolean isOwner(Vacancy vacancy,long profileId);
}
