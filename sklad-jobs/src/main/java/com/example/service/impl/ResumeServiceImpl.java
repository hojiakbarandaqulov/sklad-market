package com.example.service.impl;

import com.example.dto.resume.ResumeDTO;
import com.example.dto.resume.ResumeRequest;
import com.example.entity.Resume;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.ResumeRepository;
import com.example.service.ResourceBundleService;
import com.example.service.ResumeService;
import com.example.utils.SpringSecurityUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeServiceImpl implements ResumeService {
    private final ResumeRepository resumeRepository;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;
    private final com.example.repository.JobApplicationRepository applicationRepository;
    private final com.example.config.clent.CompanyClient companyClient;

    @Override
    @Transactional
    public ResumeDTO createResume(ResumeRequest request, AppLanguage language) {
        Long candidateId = requireProfile(language);
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        mapRequest(request, resume);
        return toDTO(resumeRepository.save(resume));
    }

    @Override
    public PageImpl<ResumeDTO> getMyResumes(int page, int perPage, AppLanguage language) {
        Long candidateId = requireProfile(language);
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("resume.pagination.invalid", language));
        }
        Pageable pageable = PageRequest.of(page - 1, perPage,
                Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id")));
        Page<Resume> resumes = resumeRepository.findAllByCandidateIdAndDeletedFalse(candidateId, pageable);
        return new PageImpl<>(resumes.getContent().stream().map(this::toDTO).toList(),
                pageable, resumes.getTotalElements());
    }

    @Override
    public ResumeDTO getResume(Long id, AppLanguage language) {
        Long viewerId = requireProfile(language);
        validateResumeId(id, language);
        Optional<Resume> owned = resumeRepository.findByIdAndCandidateIdAndDeletedFalse(id, viewerId);
        if (owned.isPresent()) return toDTO(owned.get());

        Resume resume = resumeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> resumeNotFound(language));
        var companyIds = companyClient.getOwnedCompanyIds(viewerId);
        if (companyIds != null && !companyIds.isEmpty()
                && applicationRepository.existsResumeApplicationForCompanies(
                id, resume.getCandidateId(), companyIds)) {
            return toDTO(resume);
        }
        throw resumeNotFound(language);
    }

    @Override
    @Transactional
    public ResumeDTO updateResume(Long id, ResumeRequest request, AppLanguage language) {
        Resume resume = findOwnedResume(id, requireProfile(language), language);
        mapRequest(request, resume);
        return toDTO(resumeRepository.save(resume));
    }

    @Override
    @Transactional
    public void deleteResume(Long id, AppLanguage language) {
        Resume resume = findOwnedResume(id, requireProfile(language), language);
        resume.setDeleted(true);
        resumeRepository.save(resume);
    }

    @Override
    public ResumeDTO getByIdResume(Long id, AppLanguage language) {
        Optional<Resume> byIdAndDeletedFalse = resumeRepository.findByIdAndDeletedFalse(id);
        if (byIdAndDeletedFalse.isEmpty()){
            throw resumeNotFound(language);
        }
        return toDTO(byIdAndDeletedFalse.get());
    }

    private AppNotFoundException resumeNotFound(AppLanguage language) {
        return new AppNotFoundException(messageService.getMessage("resume.not.found", language));
    }

    private void validateResumeId(Long id, AppLanguage language) {
        if (id == null || id <= 0) {
            throw new AppBadException(messageService.getMessage("resume.id.invalid", language));
        }
    }

    private Resume findOwnedResume(Long id, Long candidateId, AppLanguage language) {
        if (id == null || id <= 0) {
            throw new AppBadException(messageService.getMessage("resume.id.invalid", language));
        }
        return resumeRepository.findByIdAndCandidateIdAndDeletedFalse(id, candidateId)
                .orElseThrow(() -> new AppNotFoundException(
                        messageService.getMessage("resume.not.found", language)));
    }

    private Long requireProfile(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null || profileId <= 0) {
            throw new AuthenticationCredentialsNotFoundException(
                    messageService.getMessage("auth.buyer.profile.required", language));
        }
        return profileId;
    }

    private void mapRequest(ResumeRequest request, Resume resume) {
        resume.setTitle(request.getTitle().trim());
        resume.setFullName(request.getFullName().trim());
        resume.setPhone(request.getPhone().trim());
        resume.setEmail(request.getEmail().trim());
        resume.setRegionId(request.getRegionId());
        resume.setDesiredPosition(request.getDesiredPosition());
        resume.setExpectedSalary(request.getExpectedSalary());
        resume.setAboutMe(request.getAboutMe());
        resume.setSkills(request.getSkills());
        resume.setWorkExperience(request.getWorkExperience());
        resume.setEducation(request.getEducation());
        resume.setLanguages(request.getLanguages());
    }

    private ResumeDTO toDTO(Resume resume) {
        return modelMapper.map(resume, ResumeDTO.class);
    }
}