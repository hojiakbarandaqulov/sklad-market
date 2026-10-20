package org.example.service;

import jakarta.validation.Valid;
import org.example.dto.auth.GoogleLoginResponse;
import org.example.dto.auth.GoogleLoginRequest;

public interface GoogleLoginService {
    GoogleLoginResponse login(@Valid GoogleLoginRequest request);
}
