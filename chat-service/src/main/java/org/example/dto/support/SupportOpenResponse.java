package org.example.dto.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.enums.RequesterRole;
import org.example.enums.SupportThreadStatus;

@Getter
@AllArgsConstructor
public class SupportOpenResponse {
    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("requester_role")
    private RequesterRole requesterRole;

    private SupportThreadStatus status;

    @JsonProperty("is_new")
    private boolean isNew;
}
