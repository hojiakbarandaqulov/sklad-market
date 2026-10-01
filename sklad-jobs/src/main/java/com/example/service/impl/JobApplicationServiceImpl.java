package com.example.service.impl;

import com.example.dto.application.*;
import com.example.dto.resume.ResumeDTO;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.*;
import com.example.repository.*;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.Order;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationServiceImpl implements JobApplicationService {
    private static final EnumSet<ApplicationStatus> ACTIVE = EnumSet.of(ApplicationStatus.NEW, ApplicationStatus.REVIEWED, ApplicationStatus.IN_COMMUNICATION, ApplicationStatus.INVITED);
    private final JobApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final VacancyRepository vacancyRepository;
    private final ResourceBundleService messageService;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final com.example.config.clent.CompanyClient companyClient;

    @Override
    @Transactional
    public JobApplicationDTO create(Long vacancyId, ApplicationCreateRequest request, AppLanguage language) {
        Long candidateId = requireProfile(language);
        validateId(vacancyId, language);
        if (!Boolean.TRUE.equals(request.getConsentAccepted()))
            throw new AppBadException(messageService.getMessage("application.consent.required", language));
        if (request.getResumeId() == null || request.getResumeId() <= 0)
            throw new AppBadException(messageService.getMessage("resume.id.invalid", language));
        // Bir vakansiyaga parallel yuborilgan arizalar navbat bilan tekshiriladi.
        Vacancy vacancy = vacancyRepository.findForApplication(vacancyId).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("application.vacancy.not.found", language)));
        if (vacancy.getVacancyStatus() != VacancyStatus.PUBLISHED)
            throw new AppConflictException(messageService.getMessage("application.vacancy.closed", language));
        Resume resume = resumeRepository.findByIdAndCandidateIdAndDeletedFalse(request.getResumeId(), candidateId).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("resume.not.found", language)));
        if (applicationRepository.existsByVacancyIdAndCandidateIdAndDeletedFalse(vacancyId, candidateId))
            throw new AppConflictException(messageService.getMessage("application.duplicate", language));
        JobApplication application = new JobApplication();
        application.setVacancy(vacancy);
        application.setCandidateId(candidateId);
        application.setFullName(request.getFullName());
        application.setPhone(request.getPhone());
        application.setEmail(request.getEmail());
        application.setRegionId(request.getRegionId());
        application.setExpectedSalary(request.getExpectedSalary());
        application.setCoverLetter(request.getCoverLetter());
        application.setResumeId(resume.getId());
        application.setResumeSnapshot(snapshot(resume));
        application.setStatus(ApplicationStatus.NEW);
        application.setConsentAcceptedAt(Instant.now());
        application.setSource("SKLAD_JOBS");
        application.setChatPending(true);
        application.setChatNextAttemptAt(Instant.now());
        return toDTO(applicationRepository.save(application));
    }

    @Override
    public PageImpl<JobApplicationDTO> getMyApplications(ApplicationStatus status, int page, int perPage, AppLanguage language) {
        Long candidateId = requireProfile(language);
        if (page < 1 || perPage < 1 || perPage > 100)
            throw new AppBadException(messageService.getMessage("application.pagination.invalid", language));
        Pageable pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id")));
        Page<JobApplication> result = applicationRepository.findMyApplications(candidateId, status, pageable);
        return new PageImpl<>(result.getContent().stream().map(this::toDTO).toList(), pageable, result.getTotalElements());
    }

    @Override
    public JobApplicationDTO getMyApplication(Long id, AppLanguage language) {
        Long candidateId = requireProfile(language);
        validateId(id, language);
        return toDTO(applicationRepository.findByIdAndCandidateIdAndDeletedFalse(id, candidateId).orElseThrow(() -> notFound(language)));
    }

    @Override
    @Transactional
    public JobApplicationDTO withdraw(Long id, AppLanguage language) {
        JobApplication application = ownedForUpdate(id, language);
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) return toDTO(application);
        requireActive(application, language);
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setWithdrawnAt(Instant.now());
        return toDTO(applicationRepository.save(application));
    }

    @Override
    @Transactional
    public JobApplicationDTO replaceResume(Long id, ApplicationResumeRequest request, AppLanguage language) {
        JobApplication application = ownedForUpdate(id, language);
        requireActive(application, language);
        if (request.getResumeId() == null || request.getResumeId() <= 0)
            throw new AppBadException(messageService.getMessage("resume.id.invalid", language));
        Resume resume = resumeRepository.findByIdAndCandidateIdAndDeletedFalse(request.getResumeId(), application.getCandidateId()).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("resume.not.found", language)));
        application.setResumeSnapshot(snapshot(resume));
        application.setResumeId(resume.getId());
        return toDTO(applicationRepository.save(application));
    }

    @Override
    @Transactional
    public JobApplicationDTO applicationStatusResponse(Long id, ApplicationResponseStatus applicationResponseStatus, AppLanguage language) {
        Optional<JobApplication> jobApplication = applicationRepository.findForChat(id);
        if (jobApplication.isEmpty()) {
            throw new AppNotFoundException(messageService.getMessage("resume.not.found", language));
        }
        JobApplication jobApplicationEntity = jobApplication.get();
        requireCompanyOwner(jobApplicationEntity.getVacancy().getCompanyId());
        if(applicationResponseStatus==null) throw new AppBadException("Status required");
        requireActive(jobApplicationEntity,language);
        if(jobApplicationEntity.getFirstResponseAt()==null) jobApplicationEntity.setFirstResponseAt(Instant.now());
        ApplicationStatus applicationResponse = switch (applicationResponseStatus) {
            case ACCEPTED -> ApplicationStatus.ACCEPTED;
            case REJECTED -> ApplicationStatus.REJECTED;
        };
        jobApplicationEntity.setStatus(applicationResponse);
        return toDTO(applicationRepository.save(jobApplicationEntity));

    }

    @Override
    public Page<JobApplicationDTO> getApplicationVacancy(Long vacancyId, GetNewApplicationStatus status, int page, int perPage) {
        var vacancy=vacancyRepository.findByIdAndDeletedFalse(vacancyId)
            .orElseThrow(()->new AppNotFoundException("Vacancy not found"));
        requireCompanyOwner(vacancy.getCompanyId());
        if(page<1 || perPage<1 || perPage>100) throw new AppBadException("Invalid pagination");
        if(status==null) status=GetNewApplicationStatus.NEW;

        Pageable pageable = PageRequest.of(
                page - 1,
                perPage,
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
        ApplicationStatus applicationStatus =switch (status){
            case NEW -> ApplicationStatus.NEW;
        };
        Page<JobApplication> applications =
                applicationRepository
                        .findByVacancyIdAndDeletedFalseAndStatus(
                                vacancyId,
                                applicationStatus,
                                pageable
                        );

        return applications.map(this::toDTO);
    }

    private void requireCompanyOwner(Long companyId) {
        Long userId=SpringSecurityUtil.getProfileId();
        if(userId==null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        var owned=companyClient.getOwnedCompanyIds(userId);
        if(owned==null || !owned.contains(companyId))
            throw new org.springframework.security.access.AccessDeniedException("Company access denied");
    }

    private String snapshot(Resume resume) {
        try {
            return objectMapper.writeValueAsString(modelMapper.map(resume, ResumeDTO.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize resume snapshot", e);
        }
    }

    private JobApplication ownedForUpdate(Long id, AppLanguage language) {
        Long candidateId = requireProfile(language);
        validateId(id, language);
        return applicationRepository.findOwnedForUpdate(id, candidateId).orElseThrow(() -> notFound(language));
    }

    private void requireActive(JobApplication application, AppLanguage language) {
        if (application.getStatus() == null || !ACTIVE.contains(application.getStatus()))
            throw new AppConflictException(messageService.getMessage("application.status.conflict", language));
    }

    private void validateId(Long id, AppLanguage language) {
        if (id == null || id <= 0)
            throw new AppBadException(messageService.getMessage("application.id.invalid", language));
    }

    private AppNotFoundException notFound(AppLanguage language) {
        return new AppNotFoundException(messageService.getMessage("application.not.found", language));
    }

    private Long requireProfile(AppLanguage language) {
        Long id = SpringSecurityUtil.getProfileId();
        if (id == null || id <= 0)
            throw new AuthenticationCredentialsNotFoundException(messageService.getMessage("auth.buyer.profile.required", language));
        return id;
    }

    private JobApplicationDTO toDTO(JobApplication a) {
        JobApplicationDTO dto = new JobApplicationDTO();
        dto.setId(a.getId());
        dto.setVacancyId(a.getVacancy().getId());
        dto.setCompanyId(a.getVacancy().getCompanyId());
        dto.setPositionName(a.getVacancy().getPositionName());
        dto.setResumeId(a.getResumeId());
        dto.setChatThreadId(a.getChatThreadId());
        dto.setStatus(a.getStatus());
        dto.setFullName(a.getFullName());
        dto.setPhone(a.getPhone());
        dto.setEmail(a.getEmail());
        dto.setRegionId(a.getRegionId());
        dto.setExpectedSalary(a.getExpectedSalary());
        dto.setCoverLetter(a.getCoverLetter());
        dto.setConsentAcceptedAt(a.getConsentAcceptedAt());
        dto.setReviewedAt(a.getReviewedAt());
        dto.setWithdrawnAt(a.getWithdrawnAt());
        dto.setCreatedDate(a.getCreatedDate());
        dto.setModifiedDate(a.getModifiedDate());
        return dto;
    }

}