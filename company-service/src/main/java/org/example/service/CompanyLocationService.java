package org.example.service;

import org.example.dto.CompanyLocationResponse;

import java.util.List;

public interface CompanyLocationService {

    List<CompanyLocationResponse> getLocations();
}
