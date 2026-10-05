package com.example.service;

import com.example.dto.resume.ResumeDTO;
import com.example.dto.resume.ResumeRequest;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

public interface ResumeService {
    ResumeDTO createResume(ResumeRequest request, AppLanguage language);
    PageImpl<ResumeDTO> getMyResumes(int page, int perPage, AppLanguage language);
    ResumeDTO getResume(Long id, AppLanguage language);
    ResumeDTO updateResume(Long id, ResumeRequest request, AppLanguage language);
    void deleteResume(Long id, AppLanguage language);

    ResumeDTO getByIdResume(Long id, AppLanguage language);
}