package org.example.dto.context;

import org.example.enums.AssignedAdminRole;

/**
 * Joriy adminning profile IDsi va supportdagi admin rolini saqlaydi.
 */
public record AdminContext(
        Long userId,
        AssignedAdminRole role
) {
}
