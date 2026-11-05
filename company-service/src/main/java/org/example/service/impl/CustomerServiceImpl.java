package org.example.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.internal.FileClient;
import org.example.dto.ApiResponse;
import org.example.dto.attach.AttachDto;
import org.example.dto.customer.*;
import org.example.entity.Company;
import org.example.entity.Customer;
import org.example.enums.AppLanguage;
import org.example.exp.CustomerException;
import org.example.repository.CompanyRepository;
import org.example.repository.CustomerRepository;
import org.example.utils.SpringSecurityUtil;
import org.example.service.CustomerService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {
    private static final long MAX_CONTRACT_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> CONTRACT_TYPES = Map.of(
            "pdf", "application/pdf", "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final CustomerRepository customers;
    private final CompanyRepository companies;
    private final FileClient files;

    @Override
    @Transactional
    public CustomerResponse create(CustomerCreateRequest request, MultipartFile file, AppLanguage language) {
        Company company = companies.findByIdAndDeletedAtIsNull(request.getCompanyId())
                .orElseThrow(() -> new CustomerException(404, "customer.company.not.found"));
        requireOwner(company);
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setName(request.getName().strip());
        customer.setContractNumber(request.getContractNumber().strip());
        customer.setPhone(normalizePhone(request.getPhone()));
        customer.setPaymentTermDays(request.getPaymentTermDays());
        if (file != null) {
            attach(customer, file, language);
        }
        return response(customers.saveAndFlush(customer));
    }

    @Override
    public CustomerListResponse list(Long companyId, String search, Boolean hasFile, int page, int perPage) {
        Company company = companies.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new CustomerException(404, "customer.company.not.found"));
        requireOwner(company);
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        var result = customers.search(companyId, term, hasFile,
                PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "createdDate", "id")));
        CustomerSummary summary = customers.summarize(companyId, term, hasFile);
        if (summary.averagePaymentTermDays() == null) {
            summary = new CustomerSummary(summary.customersCount(), summary.withContractFileCount(), 0.0);
        }
        return new CustomerListResponse(result.getContent().stream().map(this::response).toList(),
                new CustomerListResponse.Meta(result.getTotalElements(), page, perPage, result.getTotalPages()), summary);
    }

    @Override
    public CustomerResponse get(Long id) {
        return response(ownedCustomer(id));
    }

    @Override
    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        Customer customer = ownedCustomer(id);
        if (request.name() != null) customer.setName(request.name().strip());
        if (request.contractNumber() != null) customer.setContractNumber(request.contractNumber().strip());
        if (request.phone() != null) customer.setPhone(normalizePhone(request.phone()));
        if (request.paymentTermDays() != null) customer.setPaymentTermDays(request.paymentTermDays());
        return response(customers.saveAndFlush(customer));
    }

    @Override
    @Transactional
    public void archive(Long id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new CustomerException(404, "customer.not.found"));
        requireOwner(customer.getCompany());
        customer.setDeleted(true);
        customers.saveAndFlush(customer);
    }

    @Override
    @Transactional
    public CustomerResponse uploadContract(Long id, MultipartFile file, AppLanguage language) {
        Customer customer = ownedCustomer(id);
        attach(customer, file, language);
        return response(customers.saveAndFlush(customer));
    }

    @Override
    public ResponseEntity<byte[]> downloadContract(Long id) {
        Customer customer = ownedCustomer(id);
        if (customer.getContractAttachId() == null) {
            throw new CustomerException(404, "customer.contract.not.found");
        }
        ResponseEntity<byte[]> result;
        try {
            result = files.open(customer.getContractAttachId());
        } catch (FeignException ex) {
            throw new CustomerException(502, "customer.file.download.failed");
        }
        if (result == null || !result.getStatusCode().is2xxSuccessful() || result.getBody() == null) {
            throw new CustomerException(502, "customer.file.invalid.response");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(customer.getContractContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(customer.getContractFileName(), StandardCharsets.UTF_8).build().toString())
                .body(result.getBody());
    }

    private Customer ownedCustomer(Long id) {
        Customer customer = customers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomerException(404, "customer.not.found"));
        requireOwner(customer.getCompany());
        return customer;
    }

    private void requireOwner(Company company) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) throw new CustomerException(401, "customer.profile.required");
        if (!Objects.equals(profileId, company.getOwnerUserId()) || company.getDeletedAt() != null
                || Boolean.TRUE.equals(company.getDeleted()) || Boolean.TRUE.equals(company.getIsBlocked())) {
            throw new CustomerException(403, "customer.company.forbidden");
        }
    }

    private void attach(Customer customer, MultipartFile file, AppLanguage language) {
        if (file.isEmpty()) throw new CustomerException(400, "customer.file.empty");
        if (file.getSize() > MAX_CONTRACT_BYTES) throw new CustomerException(413, "customer.file.too.large");
        String name = Objects.toString(file.getOriginalFilename(), "").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = CONTRACT_TYPES.get(extension);
        if (contentType == null || name.length() > 255) {
            throw new CustomerException(400, "customer.file.unsupported");
        }
        ApiResponse<AttachDto> result;
        try {
            result = files.upload(file, language.name());
        } catch (FeignException ex) {
            int status = ex.status() == 400 ? 400 : ex.status() == 413 ? 413 : 502;
            throw new CustomerException(status, "customer.file.upload.failed");
        }
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || result.getData() == null
                || result.getData().getId() == null || result.getData().getId().isBlank()) {
            throw new CustomerException(502, "customer.file.invalid.response");
        }
        String newId = result.getData().getId();
        String oldId = customer.getContractAttachId();
        // A remote upload is outside the database transaction. Compensate a rollback,
        // and keep the old file available until replacement has committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (oldId != null && !oldId.equals(newId)) deleteFile(oldId, language);
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteFile(newId, language);
                } else {
                    log.warn("Unknown transaction outcome for contract attachment {}; retaining file", newId);
                }
            }
        });
        customer.setContractAttachId(newId);
        customer.setContractFileName(name);
        customer.setContractContentType(contentType);
        customer.setContractSizeBytes(file.getSize());
    }

    private void deleteFile(String id, AppLanguage language) {
        try {
            files.delete(id, language.name());
        } catch (RuntimeException ex) {
            log.warn("Could not clean up customer contract attachment {}", id, ex);
        }
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.strip();
    }

    private CustomerResponse response(Customer customer) {
        CustomerResponse.ContractFile contract = customer.getContractAttachId() == null ? null
                : new CustomerResponse.ContractFile(customer.getContractFileName(), customer.getContractContentType(),
                    customer.getContractSizeBytes(), "/api/v1/customers/" + customer.getId() + "/contract");
        return new CustomerResponse(customer.getId(), customer.getCompany().getId(), customer.getName(),
                customer.getContractNumber(), customer.getPhone(), customer.getPaymentTermDays(), contract,
                customer.getCreatedDate(), customer.getModifiedDate());
    }
}
