package org.example.service;

import org.example.entity.Users;
import org.springframework.security.oauth2.jwt.Jwt;

public interface GoogleProfileService {
    Users findOrCreate(Jwt googleToken);

    Users provision(Long userId);

    void recordLogin(Long userId);

}
