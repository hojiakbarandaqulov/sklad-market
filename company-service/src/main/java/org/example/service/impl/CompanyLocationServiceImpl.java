package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.CompanyLocationResponse;
import org.example.enums.VerificationStatus;
import org.example.repository.CompanyLocationRepository;
import org.example.service.CompanyLocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CompanyLocationServiceImpl implements CompanyLocationService {

    private final CompanyLocationRepository companyLocationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CompanyLocationResponse> getLocations() {
        List<VerificationStatus> statuses = List.of(
                VerificationStatus.VERIFIED, VerificationStatus.PENDING_VERIFICATION);
        return Stream.concat(
                        companyLocationRepository.findCompanyLocations(statuses).stream(),
                        companyLocationRepository.findBranchLocations(statuses).stream())
                .sorted(Comparator.comparing(CompanyLocationResponse::address, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CompanyLocationResponse::type)
                        .thenComparing(CompanyLocationResponse::id))
                .toList();
    }
}
