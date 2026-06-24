package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.TokenResponseDTO;
import org.example.enums.Roles;
import org.example.exp.AppBadException;
import org.example.service.KeycloakService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.management.relation.Role;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakServiceImpl implements KeycloakService {
    @Value("${keycloak.token-url}")
    private String tokenUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin-url}")
    private String adminUrl;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.master-token-url}")
    private String masterTokenUrl;

    private final RestTemplate restTemplate;

    @Override
    public TokenResponseDTO getToken(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", username);
        body.add("password", password);

        return getTokenResponseDTO(headers, body);
    }

    @Override
    public TokenResponseDTO refreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        return getTokenResponseDTO(headers, body);
    }

    @Override
    public void assignRoleToUser(String keycloakId, Roles roleName) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        createRoleIfNotExists(roleName, adminToken); // ← qo'shing

        assignRoleToUser(keycloakId, roleName, adminToken, headers);

        log.info("Keycloak da role yangilandi: keycloakId={}, role={}", keycloakId, roleName);
    }

    @Override
    public String createUser(String firstName, String lastName, String username,
                             String password, Roles roleName) {
        try {
            String adminToken = getAdminToken();
            createRoleIfNotExists(roleName, adminToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminToken);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("username", username);
            user.put("email", username);
            user.put("firstName", firstName);
            user.put("lastName", lastName);
            user.put("enabled", true);
            user.put("emailVerified", true);
            user.put("requiredActions", Collections.emptyList());

            Map<String, Object> credential = new HashMap<>();
            credential.put("type", "password");
            credential.put("value", password);
            credential.put("temporary", false);
            user.put("credentials", List.of(credential));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(user, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(adminUrl + "/users", request, Void.class);

            String location = response.getHeaders().getFirst("Location");
            String keycloakId = location.substring(location.lastIndexOf("/") + 1);

            log.info("Keycloak da user yaratildi: {}, keycloakId: {}", username, keycloakId);

            // ROL ASSIGN QILISH
            assignRoleToUser(keycloakId, roleName, adminToken, headers);

            log.info("User {} ga {} roli assign qilindi", username, roleName);
            return keycloakId;

        } catch (Exception e) {
            log.error("Keycloak da user yaratishda xato: {}", e.getMessage(), e);
            throw new AppBadException("Keycloak da user yaratishda xato: " + e.getMessage());
        }
    }


    @Override
    public void removeRole(String keycloakId, Roles role) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        removeRoleFromUser(keycloakId, role, adminToken, headers);

        log.info("Role {} o'chirildi: keycloakId={}", role, keycloakId);
    }

    @Override
    public void updatePassword(String keycloakId, String newPassword) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", newPassword);
        credential.put("temporary", false);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(credential, headers);
        restTemplate.exchange(adminUrl + "/users/" + keycloakId + "/reset-password",
                HttpMethod.PUT, request, Void.class);

    }

    private void createRoleIfNotExists(Roles roleName, String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(adminUrl + "/roles/" + roleName,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            log.info("Rol {} allaqachon mavjud", roleName);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Rol yaratish
                Map<String, Object> role = new HashMap<>();
                role.put("name", roleName);
                role.put("description", roleName);

                HttpEntity<Map> request = new HttpEntity<>(role, headers);
                restTemplate.postForEntity(adminUrl + "/roles", request, Void.class);
                log.info("Yangi rol yaratildi: {}", roleName);
            }
        }
    }


    private void assignRoleToUser(String userId, Roles roleName, String adminToken, HttpHeaders headers) {
        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    adminUrl + "/roles/" + roleName,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (!roleResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Rol topilmadi: " + roleName);
            }

            String roleId = (String) roleResponse.getBody().get("id");

            Map<String, Object> roleMapping = new HashMap<>();
            roleMapping.put("id", roleId);
            roleMapping.put("name", roleName);

            HttpEntity<List> roleAssignRequest = new HttpEntity<>(List.of(roleMapping), headers);
            ResponseEntity<Void> assignResponse = restTemplate.postForEntity(
                    adminUrl + "/users/" + userId + "/role-mappings/realm",
                    roleAssignRequest,
                    Void.class
            );

            if (!assignResponse.getStatusCode().is2xxSuccessful()) {
                log.warn("Rol assign da muammo: {}", assignResponse.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Rol assign xatosi - userId: {}, role: {}: {}", userId, roleName, e.getMessage());
            throw new RuntimeException("Rol assign qilishda xato: " + e.getMessage());
        }
    }


    @Override
    public void deleteUser(String username) {

    }

    @Override
    public void addProfileIdAttribute(String keycloakId, Long profileId,
                                      String firstName, String lastName, String email, String password) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("profileId", List.of(String.valueOf(profileId)));

        // Password credential ni ham qo'shing
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", password);
        credential.put("temporary", false);

        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("firstName", firstName);
        userUpdate.put("lastName", lastName);
        userUpdate.put("email", email);
        userUpdate.put("emailVerified", true);
        userUpdate.put("enabled", true);
        userUpdate.put("credentials", List.of(credential)); // ← qo'shing
        userUpdate.put("attributes", attributes);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userUpdate, headers);
        restTemplate.exchange(
                adminUrl + "/users/" + keycloakId,
                HttpMethod.PUT,
                request,
                Void.class
        );
    }
//    @Override
//    public void verifyUserEmail(String username) {
//        try {
//            String adminToken = getAdminToken();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            headers.setBearerAuth(adminToken);
//
//            // Avval userId ni topish
//            HttpEntity<Void> getRequest = new HttpEntity<>(headers);
//            ResponseEntity<List> response = restTemplate.exchange(
//                    adminUrl + "/users?username=" + username,
//                    HttpMethod.GET, getRequest, List.class);
//
//            List<Map<String, Object>> users = response.getBody();
//            if (users != null && !users.isEmpty()) {
//                String userId = (String) users.get(0).get("id");
//
//                // Email tasdiqlash — DELETE emas, PUT!
//                Map<String, Object> updateData = new HashMap<>();
//                updateData.put("emailVerified", true);
//
//                HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateData, headers);
//                restTemplate.exchange(
//                        adminUrl + "/users/" + userId,
//                        HttpMethod.PUT, updateRequest, Void.class);
//
//                log.info("Keycloak da email tasdiqlandi: {}", username);
//            }
//        } catch (Exception e) {
//            log.warn("Keycloak da email tasdiqlashda xato: {}", e.getMessage());
//        }
//    }

    public String getAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Token request body: {}", body);  // DEBUG
            ResponseEntity<TokenResponseDTO> response = restTemplate.postForEntity(
                    masterTokenUrl,
                    request, TokenResponseDTO.class);  // TokenResponse DTO yarating

            if (response.getStatusCode().is2xxSuccessful()) {
                String token = response.getBody().getAccessToken();
                log.info("Admin token muvaffaqiyatli olindi");
                return token;
            } else {
                log.error("Token response status: {}, body: {}", response.getStatusCode(), Optional.ofNullable(response.getBody()));
            }
        } catch (HttpClientErrorException e) {
            log.error("Token error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        }
        throw new RuntimeException("Admin token olinmadi");
    }

    private void removeRoleFromUser(String userId, Roles roleName, String adminToken, HttpHeaders headers) {
        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    adminUrl + "/roles/" + roleName,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            String roleId = (String) roleResponse.getBody().get("id");

            Map<String, Object> roleMapping = new HashMap<>();
            roleMapping.put("id", roleId);
            roleMapping.put("name", roleName.name());

            HttpEntity<List> request = new HttpEntity<>(List.of(roleMapping), headers);
            restTemplate.exchange(
                    adminUrl + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.DELETE,
                    request,
                    Void.class
            );
            log.info("Role {} o'chirildi: userId={}", roleName, userId);
        } catch (Exception e) {
            log.warn("Role o'chirishda xato: {}", e.getMessage());
        }
    }

    @NotNull
    private TokenResponseDTO getTokenResponseDTO(HttpHeaders headers, MultiValueMap<String, String> body) {
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map responseBody = response.getBody();

        assert responseBody != null;
        return new TokenResponseDTO(
                (String) responseBody.get("access_token"),
                (String) responseBody.get("refresh_token"),
                (Integer) responseBody.get("expires_in")
        );
    }
}
