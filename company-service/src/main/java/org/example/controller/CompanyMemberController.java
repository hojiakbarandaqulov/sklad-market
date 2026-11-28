package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.member.CompanyMemberCreateRequest;
import org.example.dto.member.CompanyMemberResponse;
import org.example.enums.AppLanguage;
import org.example.exp.CompanyMemberException;
import org.example.service.CompanyMemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/companies/{companyId}/members")
public class CompanyMemberController {
    private final CompanyMemberService service;

    @GetMapping
    @Operation(summary = "Kompaniyaning faol a'zolari")
    public ApiResponse<List<CompanyMemberResponse>> list(@PathVariable @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.list(companyId, language));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kompaniyaga a'zo qo'shish", description = "Kompaniya administratori mavjud profil ID'sini yuboradi.")
    public ApiResponse<CompanyMemberResponse> add(@PathVariable @Positive Long companyId,
            @RequestBody @Valid CompanyMemberCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.add(companyId, request.profileId(), language));
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Kompaniya a'zoligini bekor qilish")
    public void remove(@PathVariable @Positive Long companyId, @PathVariable @Positive Long profileId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        service.remove(companyId, profileId, language);
    }

    @ExceptionHandler(CompanyMemberException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberError(CompanyMemberException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiResponse<>(exception.getMessage(), false));
    }
}
