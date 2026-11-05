package org.example.service;

import org.example.dto.customer.CustomerCreateRequest;
import org.example.dto.customer.CustomerListResponse;
import org.example.dto.customer.CustomerResponse;
import org.example.dto.customer.CustomerUpdateRequest;
import org.example.enums.AppLanguage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerService {

    CustomerResponse create(CustomerCreateRequest request, MultipartFile file, AppLanguage language);

    CustomerListResponse list(Long companyId, String search, Boolean hasFile, int page, int perPage);

    CustomerResponse get(Long id);

    CustomerResponse update(Long id, CustomerUpdateRequest request);

    void archive(Long id);

    CustomerResponse uploadContract(Long id, MultipartFile file, AppLanguage language);

    ResponseEntity<byte[]> downloadContract(Long id);
}
