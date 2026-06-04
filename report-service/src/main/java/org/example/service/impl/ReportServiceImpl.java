package org.example.service.impl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.example.config.clent.CompanyClient;
import org.example.config.clent.ProductClient;
import org.example.config.clent.UserClient;
import org.example.dto.internal.CompanySummaryResponse;
import org.example.dto.internal.ProductSummaryResponse;
import org.example.dto.report.*;
import org.example.entity.Report;
import org.example.enums.AppLanguage;
import org.example.enums.ReportStatus;
import org.example.enums.TargetType;
import org.example.exp.AppBadException;
import org.example.repository.ReportRepository;
import org.example.service.ReportService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {
    private final ReportRepository repository;
    private final ResourceBundleService messageService;
    private final ModelMapper modelMapper;
    private final RestTemplate restTemplate;
    private final ProductClient productClient;
    private final CompanyClient companyClient;
    private final UserClient userClient;

    @Override
    public ReportShortResponse createReport(ReportCreateRequest reportCreateRequest) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Report report = new Report();
        report.setReporterUserId(profileId);
        report.setTargetType(reportCreateRequest.getTargetType());
        report.setTargetId(reportCreateRequest.getTargetId());
        report.setReasonCode(reportCreateRequest.getReasonCode());
        report.setComment(reportCreateRequest.getComment());
        repository.save(report);
        return new ReportShortResponse(report.getId(), report.getStatus());
    }

    @Override
    public PageImpl<ReportResponse> getReport(ReportStatus status, TargetType targetType, int page, int size, AppLanguage language) {
        int page1 = normalizePage(page, language);
        int size1 = normalizePerPage(size, language);

        Specification<Report> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (targetType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        PageRequest pageRequest = PageRequest.of(page1 - 1, size1, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Report> result = repository.findAll(spec, pageRequest);
        return new PageImpl<>(result.getContent().stream().map(this::toResponse).toList(), pageRequest, result.getTotalElements());
    }

    @Override
    public ReportInfoResponse getByReport(Long id, AppLanguage language) {
        Optional<Report> optionalReport = repository.findByIdAndDeletedFalse(id);
        if (optionalReport.isPresent()) {
            Report report = optionalReport.get();
            return modelMapper.map(report, ReportInfoResponse.class);
        }
        log.info("report not found id={}", id);
        throw new AppBadException(messageService.getMessage("report.not.found", language));
    }

    @Override
    public ReportResolveResponse reportReject(Long id, ReportResolveRequest request, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Report report = getReportEntity(id, language);
        report.setStatus(ReportStatus.REJECTED);
        report.setResolvedBy(profileId);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(request.getResolutionNote());
        repository.save(report);
        return modelMapper.map(report, ReportResolveResponse.class);
    }

    @Override
    public ReportResolveResponse reportResolve(Long id, ReportResolveRequest request, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Report report = getReportEntity(id, language);
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedBy(profileId);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(request.getResolutionNote());
        repository.save(report);
        return modelMapper.map(report, ReportResolveResponse.class);
    }

    @Override
    public AdminDashboardResponse getDashboard() {
        AdminDashboardResponse response = new AdminDashboardResponse();
        response.setPendingProducts(productClient.getPendingCount());
        response.setPendingCompanies(companyClient.getPendingCount());
        response.setBlockedUsers(userClient.blockedCount());
        response.setOpenReports(repository.countByStatusAndDeletedFalse(ReportStatus.NEW));
        return response;
    }

    @Override
    public ReportResolveResponse warnUser(Long id, ReportWarnRequest request, AppLanguage language) {
        Long userId = resolveTargetUserId(id, language);
//        restTemplate.postForEntity("http://localhost:8082/internal/profiles/{userId}/warning", null, Void.class, targetUserId);
        userClient.Warning(userId);

        Report report = getReportEntity(id, language);
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedBy(SpringSecurityUtil.getProfileId());
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(request.getMessage());
        repository.save(report);
        return modelMapper.map(report, ReportResolveResponse.class);
    }

    @Override
    public ReportResolveResponse blockTarget(Long id, ReportBlockRequest request, AppLanguage language) {
        Report report = getReportEntity(id, language);
//        Map<String, String> body = Map.of("reason", request.getReason());

        if (report.getTargetType() == TargetType.PRODUCT) {
          /*  restTemplate.exchange(
                    "http://localhost:8085/internal/products/{id}/block",
                    HttpMethod.PUT,
                    new HttpEntity<>(body),
                    Void.class,
                    report.getTargetId()
            );*/
            productClient.blockProduct(report.getTargetId(),request);
        } else if (report.getTargetType() == TargetType.COMPANY) {
          /*  restTemplate.exchange(
                    "http://localhost:8083/internal/companies/{id}/block",
                    HttpMethod.PUT,
                    new HttpEntity<>(body),
                    Void.class,
                    report.getTargetId()
            );*/
            companyClient.block(report.getTargetId(),request);
        } else if (report.getTargetType() == TargetType.CHAT) {
            restTemplate.exchange(
                    "http://localhost:8087/internal/chats/{id}/block",
                    HttpMethod.PUT,
                    HttpEntity.EMPTY,
                    Void.class,
                    report.getTargetId()
            );
        } else {
            throw new AppBadException("Unsupported target type");
        }

        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedBy(SpringSecurityUtil.getProfileId());
        report.setResolvedAt(LocalDateTime.now());
        report.setResolutionNote(request.getReason());
        repository.save(report);
        return modelMapper.map(report, ReportResolveResponse.class);
    }

    private Long resolveTargetUserId(Long reportId, AppLanguage language) {
        Report report = getReportEntity(reportId, language);
        if (report.getTargetType() == TargetType.PRODUCT) {
           /* ProductSummaryResponse product = restTemplate.getForObject(
                    "http://localhost:8085/internal/products/{id}/summary",
                    ProductSummaryResponse.class,
                    report.getTargetId()
            );*/
            ProductSummaryResponse product = productClient.getSummary(report.getTargetId());
            if (product == null || product.getSellerId() == null) {
                throw new AppBadException("product owner not found");
            }
            return product.getSellerId();
        }
        if (report.getTargetType() == TargetType.COMPANY) {
            CompanySummaryResponse company = companyClient.getSummary(report.getTargetId());
           /* CompanySummaryResponse company = restTemplate.getForObject(
                    "http://localhost:8083/internal/companies/{id}/summary",
                    CompanySummaryResponse.class,
                    report.getTargetId()
            );*/
            if (company == null || company.getOwnerUserId() == null) {
                throw new AppBadException("company owner not found");
            }
            return company.getOwnerUserId();
        }
        throw new AppBadException("Warn user action is not supported for this target type");
    }

    private Long fetchCount(String url) {
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Object count = response.getBody() == null ? null : response.getBody().get("count");
        if (count instanceof Number number) {
            return number.longValue();
        }
        if (count instanceof String value && !value.isBlank()) {
            return Long.parseLong(value);
        }
        return 0L;
    }

    private Report getReportEntity(Long id, AppLanguage language) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("report.not.found", language)));
    }

    private ReportResponse toResponse(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReasonCode(report.getReasonCode());
        response.setStatus(report.getStatus());
        response.setCreatedDate(report.getCreatedDate());
        return response;
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }

}

