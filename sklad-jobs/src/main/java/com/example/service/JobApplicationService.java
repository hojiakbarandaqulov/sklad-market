package com.example.service;
import com.example.dto.application.*;
import com.example.entity.JobApplication;
import com.example.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
public interface JobApplicationService {
    JobApplicationDTO create(Long vacancyId,ApplicationCreateRequest request,AppLanguage language);
    PageImpl<JobApplicationDTO> getMyApplications(ApplicationStatus status,int page,int perPage,AppLanguage language);
    JobApplicationDTO getMyApplication(Long id,AppLanguage language);
    JobApplicationDTO withdraw(Long id,AppLanguage language);
    JobApplicationDTO replaceResume(Long id,ApplicationResumeRequest request,AppLanguage language);

    JobApplicationDTO applicationStatusResponse(Long id, ApplicationResponseStatus applicationResponseStatus, AppLanguage language);

    Page<JobApplicationDTO> getApplicationVacancy(Long vacancyId, GetNewApplicationStatus status, int page, int perPage);

}