package org.example.dto.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SupportReadReceiptResponse {
    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("message_ids")
    private List<Long> messageIds;

    @JsonProperty("read_by")
    private Long readBy;
}
