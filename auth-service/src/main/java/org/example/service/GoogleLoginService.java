package org.example.service;

import jakarta.validation.Valid;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.GoogleLoginRequest;

public interface GoogleLoginService {
    TokenResponseDTO login(@Valid GoogleLoginRequest request);
}
