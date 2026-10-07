package com.example.service;

import com.example.dto.resume.ResumeDTO;
import com.example.entity.Resume;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import static org.assertj.core.api.Assertions.assertThat;

class ResumeImageMappingTest {
    @Test
    void resumeResponseIncludesPersistedAttachmentId() {
        Resume resume = new Resume();
        resume.setId(42L);
        resume.setFileId("image-123.png");
        ResumeDTO dto = new ModelMapper().map(resume, ResumeDTO.class);
        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getFileId()).isEqualTo("image-123.png");
    }

    @Test
    void resumeWithoutImageHasNoAttachmentId() {
        assertThat(new ModelMapper().map(new Resume(), ResumeDTO.class).getFileId()).isNull();
    }
}