package org.example.controller;

import org.example.entity.Attach;
import org.example.service.AttachService;
import org.example.enums.AppLanguage;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SaleAttachmentInternalControllerTest {
    AttachService files=mock(AttachService.class);
    SaleAttachmentInternalController controller=new SaleAttachmentInternalController(files);
    @BeforeEach void setup() {
        var jwt=Jwt.withTokenValue("seller").header("alg","RS256").claim("profileId",42L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void onlyOwnersLiveAttachmentMetadataIsReturned() {
        var attach=new Attach(); attach.setId("receipt"); attach.setCreatedBy(42L); attach.setSize(123L); attach.setMimeType("application/pdf");
        when(files.get("receipt",AppLanguage.UZ)).thenReturn(attach);
        assertThat(controller.info("receipt").size()).isEqualTo(123L);
        attach.setCreatedBy(99L);
        assertThatThrownBy(() -> controller.info("receipt")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        attach.setCreatedBy(42L); attach.setDeleted(true);
        assertThatThrownBy(() -> controller.info("receipt")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void uploadRecordsAuthenticatedOwnerForSaleAttachmentChecks() {
        var repository=mock(org.example.repository.AttachRepository.class);
        var minio=mock(io.minio.MinioClient.class);
        var messages=mock(org.example.service.ResourceBundleService.class);
        var uploads=new org.example.service.impl.AttachServiceImpl(repository,minio,messages);
        org.springframework.test.util.ReflectionTestUtils.setField(uploads,"bucketName","test-bucket");
        org.springframework.test.util.ReflectionTestUtils.setField(uploads,"baseUrl","https://example.test/");
        var file=new org.springframework.mock.web.MockMultipartFile("file","receipt.pdf","application/pdf",
                "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        uploads.uploadFile(file,AppLanguage.UZ);
        var saved=org.mockito.ArgumentCaptor.forClass(Attach.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(42L);
    }
}
