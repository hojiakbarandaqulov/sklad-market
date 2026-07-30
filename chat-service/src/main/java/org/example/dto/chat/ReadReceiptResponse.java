package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReadReceiptResponse {
    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("message_ids")
    private List<Long> messageIds;

    @JsonProperty("read_by")
    private Long readBy;
}
