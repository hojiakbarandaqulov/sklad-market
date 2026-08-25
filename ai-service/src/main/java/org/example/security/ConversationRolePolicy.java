package org.example.security;

import org.example.entity.Conversation;
import org.example.exception.AiNotFoundException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Enforces the full role snapshot under which a conversation was created. */
public final class ConversationRolePolicy {

    private ConversationRolePolicy() {
    }

    public static void requireCurrentRoles(Conversation conversation, Set<String> currentRoles) {
        requireCurrentRolesSnapshot(conversation == null ? null : conversation.getUserRole(), currentRoles);
    }

    public static void requireCurrentRolesSnapshot(String snapshot, Set<String> currentRoles) {
        Set<String> required = parse(snapshot);
        if (required.isEmpty() || required.equals(Set.of("USER"))) {
            return;
        }
        Set<String> normalizedCurrent = new LinkedHashSet<>();
        if (currentRoles != null) {
            currentRoles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.trim().toUpperCase(Locale.ROOT))
                    .forEach(normalizedCurrent::add);
        }
        if (!normalizedCurrent.containsAll(required)) {
            // Preserve owner-scoped non-enumerability: revoked-role history behaves like a missing
            // conversation instead of revealing which former role produced protected data.
            throw new AiNotFoundException("Conversation not found");
        }
    }

    private static Set<String> parse(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return Set.of();
        Set<String> roles = new LinkedHashSet<>();
        Arrays.stream(snapshot.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .forEach(roles::add);
        return roles;
    }
}
