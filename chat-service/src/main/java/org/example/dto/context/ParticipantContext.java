package org.example.dto.context;

import org.example.entity.SupportThread;
import org.example.enums.SupportParticipantRole;

/**
 * Support thread ichida joriy foydalanuvchining thread va rolini birga saqlaydi.
 */
public record ParticipantContext(
        SupportThread thread,
        SupportParticipantRole role
) {
}
