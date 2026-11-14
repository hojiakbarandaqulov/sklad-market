package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.customer.*;
import org.example.enums.AppLanguage;
import org.example.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Mijozlar", description = "Kompaniyaga tegishli mijozlar va shartnomalar")
public class CustomerController {
    private final CustomerService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mijoz yaratish", description = "companyId, name, contractNumber, phone, paymentTermDays form maydonlari. file ixtiyoriy, 2 MB gacha.")
    public ApiResponse<CustomerResponse> create(@Valid CustomerCreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.create(request, file, language));
    }

    @GetMapping
    @Operation(summary = "Mijozlar ro'yxati", description = "page 1 dan boshlanadi. summary barcha filtrlangan mijozlar bo'yicha hisoblanadi.")
    public ApiResponse<CustomerListResponse> list(@RequestParam @Positive Long companyId,
            @RequestParam(required = false) @Size(max = 255) String search,
            @RequestParam(required = false) Boolean hasContractFile,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.list(companyId, search, hasContractFile, page, perPage, language));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Bitta mijoz tafsilotlari")
    public ApiResponse<CustomerResponse> get(@PathVariable @Positive Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.get(id, language));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mijozni tahrirlash", description = "Faqat o'zgartiriladigan maydonlarni yuboring. phone bo'sh satr bo'lsa telefon tozalanadi.")
    public ApiResponse<CustomerResponse> update(@PathVariable @Positive Long id,
            @Valid @RequestBody CustomerUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.update(id, request, language));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Mijozni arxivlash", description = "Yozuv va shartnoma saqlanadi. Takroriy so'rov xavfsiz.")
    public ApiResponse<ArchiveResponse> archive(@PathVariable @Positive Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        service.archive(id, language);
        return ApiResponse.successResponse(new ArchiveResponse(id, true));
    }

    @PostMapping(value = "/{id}/contract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Shartnomani yuklash yoki almashtirish", description = "file-service upload API orqali yuklanadi. file majburiy, 2 MB gacha.")
    public ApiResponse<CustomerResponse> uploadContract(@PathVariable @Positive Long id,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.uploadContract(id, file, language));
    }

    @GetMapping("/{id}/contract")
    @Operation(summary = "Shartnoma faylini yuklab olish", description = "Kompaniya egaligi tekshiriladi, javob faylning o'zi.")
    public ResponseEntity<byte[]> downloadContract(@PathVariable @Positive Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return service.downloadContract(id, language);
    }

    public record ArchiveResponse(Long id, boolean archived) {
    }
}
