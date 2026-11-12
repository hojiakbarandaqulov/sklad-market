package com.example.service;

import com.example.dto.AddressSuggestion;
import com.example.enums.AppLanguage;

import java.util.List;

public interface AddressService {


    List<AddressSuggestion> regionSearch(String q);

}
