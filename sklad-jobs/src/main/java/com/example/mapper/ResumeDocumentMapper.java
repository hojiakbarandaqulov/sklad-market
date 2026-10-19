package com.example.mapper;

import com.example.document.ResumeDocument;
import com.example.dto.resume.ResumeDTO;
import com.example.entity.Resume;
import org.springframework.util.Assert;

public final class ResumeDocumentMapper {
    private ResumeDocumentMapper() {
    }

    public static ResumeDocument from(Resume resume) {
        Assert.notNull(resume, "Resume must not be null");
        Assert.isTrue(resume.getId() != null && resume.getId() > 0,
                "Resume must be saved in the database before indexing");
        return ResumeDocument.builder()
                .id(resume.getId().toString())
                .resumeId(resume.getId())
                .candidateId(resume.getCandidateId())
                .fileId(resume.getFileId())
                .title(resume.getTitle())
                .fullName(resume.getFullName())
                .phone(resume.getPhone())
                .email(resume.getEmail())
                .regionId(resume.getRegionId())
                .categoryId(resume.getCategoryId())
                .desiredPosition(resume.getDesiredPosition())
                .expectedSalary(resume.getExpectedSalary())
                .aboutMe(resume.getAboutMe())
                .skills(resume.getSkills())
                .workExperience(resume.getWorkExperience())
                .education(resume.getEducation())
                .languages(resume.getLanguages())
                .createdDate(resume.getCreatedDate())
                .modifiedDate(resume.getModifiedDate())
                .deleted(Boolean.TRUE.equals(resume.getDeleted()))
                .build();
    }

    public static ResumeDTO toDTO(ResumeDocument document) {
        ResumeDTO dto = new ResumeDTO();
        dto.setId(document.getResumeId());
        dto.setCandidateId(document.getCandidateId());
        dto.setFileId(document.getFileId());
        dto.setTitle(document.getTitle());
        dto.setFullName(document.getFullName());
        dto.setPhone(document.getPhone());
        dto.setEmail(document.getEmail());
        dto.setRegionId(document.getRegionId());
        dto.setCategoryId(document.getCategoryId());
        dto.setDesiredPosition(document.getDesiredPosition());
        dto.setExpectedSalary(document.getExpectedSalary());
        dto.setAboutMe(document.getAboutMe());
        dto.setSkills(document.getSkills());
        dto.setWorkExperience(document.getWorkExperience());
        dto.setEducation(document.getEducation());
        dto.setLanguages(document.getLanguages());
        dto.setCreatedDate(document.getCreatedDate());
        dto.setModifiedDate(document.getModifiedDate());
        return dto;
    }
}
