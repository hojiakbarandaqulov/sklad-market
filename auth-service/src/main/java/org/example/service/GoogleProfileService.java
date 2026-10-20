package org.example.service;

import org.example.entity.Users;
import org.springframework.security.oauth2.jwt.Jwt;

public interface GoogleProfileService {
    Users prepare(Jwt googleToken, String keycloakId);

}
