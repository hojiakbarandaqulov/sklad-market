package org.example.mapper;

import org.example.dto.member.CompanyMemberResponse;
import org.example.entity.CompanyMember;
import org.example.enums.CompanyMemberRole;
import org.springframework.stereotype.Component;

@Component
public class CompanyMemberMapper {
    public CompanyMember toEntity(Long companyId, Long profileId, CompanyMemberRole role, Long actorProfileId) {
        CompanyMember member = new CompanyMember();
        member.setCompanyId(companyId);
        member.setProfileId(profileId);
        member.setRole(role);
        member.setCreatedBy(actorProfileId);
        return member;
    }

    public CompanyMemberResponse toResponse(CompanyMember member) {
        return new CompanyMemberResponse(member.getId(), member.getCompanyId(), member.getProfileId(),
                member.getRole(), member.getCreatedDate(), member.getCreatedBy());
    }
}
