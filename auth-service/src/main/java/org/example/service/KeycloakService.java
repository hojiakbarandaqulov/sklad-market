package org.example.service;

import org.example.dto.TokenResponseDTO;
import org.example.enums.Roles;
import org.springframework.http.HttpHeaders;

import javax.management.relation.Role;

public interface KeycloakService {
    TokenResponseDTO getToken(String username, String password);

    TokenResponseDTO refreshToken(String refreshToken);

    String createUser(String firstName, String lastName, String username, String password, Roles role);

    void deleteUser(String username);

    void addProfileIdAttribute(String keycloakId, Long profileId,
                               String firstName, String lastName, String email, String password);

    void assignRoleToUser(String keycloakId, Roles roleName);

    //    void verifyUserEmail(String username);
    void removeRole(String keycloakId, Roles role);

    void updatePassword(String keycloakId, String newPassword);

    void revokeUserSessions(String keycloakId);

}
