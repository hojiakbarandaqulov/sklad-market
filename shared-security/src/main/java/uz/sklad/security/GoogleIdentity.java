package uz.sklad.security;

/** Platform identity resolved from a verified Google subject, never from request claims alone. */
public record GoogleIdentity(Long profileId, String username, String role, String keycloakId) {
}
