package org.example.service;

import org.example.dto.member.CompanyMemberResponse;
import org.example.enums.AppLanguage;

import java.util.List;

public interface CompanyMemberService {
    List<CompanyMemberResponse> list(Long companyId, AppLanguage language);

    CompanyMemberResponse add(Long companyId, Long profileId, AppLanguage language);

    void remove(Long companyId, Long profileId, AppLanguage language);
}
