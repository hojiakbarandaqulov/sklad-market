package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.config.clent.FileClient;
import com.example.config.clent.dto.AttachDto;
import com.example.dto.ApiResponse;
import com.example.entity.Resume;
import com.example.enums.AppLanguage;
import com.example.repository.*;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes=ResumeImagePersistenceTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ResumeImagePersistenceTest {
    @Configuration @EnableAutoConfiguration @EntityScan("com.example.entity")
    @EnableJpaRepositories("com.example.repository") @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean FileClient files() { return mock(FileClient.class); }
        @Bean ResumeServiceImpl resumeService(ResumeRepository resumes, JobApplicationRepository applications, FileClient files) {
            return new ResumeServiceImpl(resumes, new ModelMapper(), mock(ResourceBundleService.class), applications, mock(CompanyClient.class), files);
        }
    }
    @Autowired ResumeRepository resumes;
    @Autowired ResumeServiceImpl service;
    @Autowired FileClient files;

    @Test void uploadCommitsFileIdAndFreshListAndDetailReturnIt() {
        Resume resume = new Resume();
        resume.setCandidateId(25L); resume.setTitle("Developer");
        resume.setFullName("Candidate"); resume.setPhone("+998900000000");
        resume.setEmail("candidate@example.com"); resume.setRegionId(1L);
        Long id = resumes.saveAndFlush(resume).getId();
        var file = new MockMultipartFile("file", "resume.png", "image/png", new byte[]{1,2,3});
        AttachDto attachment = new AttachDto();
        attachment.setId("resume-image.png"); attachment.setUrl("/api/v1/attach/open/resume-image.png");
        when(files.upload(file,"UZ")).thenReturn(ApiResponse.successResponse(attachment));
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
            assertEquals("resume-image.png", service.uploadImages(id,file,AppLanguage.UZ).getId());
            assertEquals("resume-image.png", resumes.findById(id).orElseThrow().getFileId());
            assertEquals("resume-image.png", service.getResume(id,AppLanguage.UZ).getFileId());
            assertEquals("resume-image.png", service.getMyResumes(1,20,AppLanguage.UZ).getContent().get(0).getFileId());
        }
    }
}