package org.example.customer;

import feign.FeignException;
import org.example.config.internal.FileClient;
import org.example.config.internal.OrderClient;
import org.example.dto.ApiResponse;
import org.example.dto.attach.AttachDto;
import org.example.dto.customer.CustomerCreateRequest;
import org.example.entity.Company;
import org.example.entity.Customer;
import org.example.enums.AppLanguage;
import org.example.exp.CustomerException;
import org.example.repository.CompanyRepository;
import org.example.repository.CustomerRepository;
import org.example.service.CustomerService;
import org.example.service.ResourceBundleService;
import org.example.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final FileClient files = mock(FileClient.class);
    private final OrderClient orderClient = mock(OrderClient.class);
    private final ResourceBundleService messageService = mock(ResourceBundleService.class);
    private final CustomerService service = new CustomerServiceImpl(customers, companies, files, orderClient, messageService);
    private Company company;
    private Customer customer;
    private final MockMultipartFile pdf = new MockMultipartFile("file", "contract.pdf", "application/pdf", "%PDF-1.7".getBytes());

    @BeforeEach
    void setup() {
        when(messageService.getMessage(anyString(), any(AppLanguage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").claim("profileId", 10L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        TransactionSynchronizationManager.initSynchronization();
        company = new Company();
        company.setId(7L);
        company.setOwnerUserId(10L);
        customer = new Customer();
        customer.setId(101L);
        customer.setCompany(company);
        customer.setName("Baraka");
        customer.setContractNumber("SH-1");
        customer.setPaymentTermDays(15);
        when(companies.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(company));
        when(customers.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(customer));
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(101L);
            return saved;
        });
        AttachDto attachment = new AttachDto();
        attachment.setId("new-file.pdf");
        attachment.setUrl("https://storage.example/private-object");
        when(files.upload(any(), eq("UZ"))).thenReturn(ApiResponse.successResponse(attachment));
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsCustomerAndUsesExistingFileUpload() {
        var request = new CustomerCreateRequest();
        request.setCompanyId(7L);
        request.setName(" Baraka ");
        request.setContractNumber(" SH-1 ");
        request.setPaymentTermDays(15);
        var result = service.create(request, pdf, AppLanguage.UZ);
        assertEquals("Baraka", result.name());
        assertEquals("SH-1", result.contractNumber());
        assertEquals("/api/v1/customers/101/contract", result.contractFile().downloadUrl());
        verify(files).upload(pdf, "UZ");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(files, never()).delete(any(), any());
    }

    @Test
    void rejectsAnotherCompanyBeforeReadingOrUploadingFiles() {
        company.setOwnerUserId(99L);
        CustomerException error = assertThrows(CustomerException.class,
                () -> service.uploadContract(101L, pdf, AppLanguage.UZ));
        assertEquals(403, error.getStatus());
        assertThrows(CustomerException.class, () -> service.downloadContract(101L, AppLanguage.UZ));
        verifyNoInteractions(files);
    }

    @Test
    void rejectsMissingProfile() {
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(CustomerException.class, () -> service.get(101L, AppLanguage.UZ)).getStatus());
    }

    @Test
    void rejectsBlockedCompanyBeforeFileAccess() {
        company.setIsBlocked(true);
        assertEquals(403, assertThrows(CustomerException.class, () -> service.downloadContract(101L, AppLanguage.UZ)).getStatus());
        verifyNoInteractions(files);
    }

    @Test
    void uploadFailureDoesNotSaveCustomer() {
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(503);
        when(files.upload(pdf, "UZ")).thenThrow(failure);
        assertEquals(502, assertThrows(CustomerException.class,
                () -> service.uploadContract(101L, pdf, AppLanguage.UZ)).getStatus());
        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void replacementDeletesOldFileOnlyAfterCommit() {
        customer.setContractAttachId("old-file.pdf");
        service.uploadContract(101L, pdf, AppLanguage.UZ);
        verify(files, never()).delete(any(), any());
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(files).delete("old-file.pdf", "UZ");
        verify(files, never()).delete("new-file.pdf", "UZ");
    }

    @Test
    void databaseConflictCleansNewUploadAndKeepsPreviousFile() {
        customer.setContractAttachId("old-file.pdf");
        when(customers.saveAndFlush(any())).thenThrow(new OptimisticLockingFailureException("conflict"));
        assertThrows(OptimisticLockingFailureException.class,
                () -> service.uploadContract(101L, pdf, AppLanguage.UZ));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(files).delete("new-file.pdf", "UZ");
        verify(files, never()).delete("old-file.pdf", "UZ");
    }

    @Test
    void uncertainTransactionOutcomeDoesNotDeleteEitherFile() {
        customer.setContractAttachId("old-file.pdf");
        service.uploadContract(101L, pdf, AppLanguage.UZ);
        complete(TransactionSynchronization.STATUS_UNKNOWN);
        verify(files, never()).delete(any(), any());
    }

    @Test
    void rejectsOversizedAndEmptyContractsBeforeUpload() {
        var large = new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[2 * 1024 * 1024 + 1]);
        assertEquals(413, assertThrows(CustomerException.class,
                () -> service.uploadContract(101L, large, AppLanguage.UZ)).getStatus());
        var empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertEquals(400, assertThrows(CustomerException.class,
                () -> service.uploadContract(101L, empty, AppLanguage.UZ)).getStatus());
        verifyNoInteractions(files);
    }

    @Test
    void archiveIsRepeatableAndRetainsContract() {
        customer.setContractAttachId("old-file.pdf");
        when(customers.findById(101L)).thenReturn(Optional.of(customer));
        service.archive(101L, AppLanguage.UZ);
        service.archive(101L, AppLanguage.UZ);
        assertTrue(customer.getDeleted());
        assertEquals("old-file.pdf", customer.getContractAttachId());
        verifyNoInteractions(files);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
