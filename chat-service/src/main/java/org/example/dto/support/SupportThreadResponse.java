package org.example.dto.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.AssignedAdminRole;
import org.example.enums.RequesterRole;
import org.example.enums.SupportThreadStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class SupportThreadResponse {
    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("requester_id")
    private Long requesterId;

    @JsonProperty("requester_role")
    private RequesterRole requesterRole;

    @JsonProperty("assigned_admin_id")
    private Long assignedAdminId;

    @JsonProperty("assigned_admin_role")
    private AssignedAdminRole assignedAdminRole;

    private SupportThreadStatus status;
    private String subject;

    @JsonProperty("last_message_at")
    private LocalDateTime lastMessageAt;

    @JsonProperty("created_date")
    private LocalDateTime createdDate;
}
