package org.example.dto.chat;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class JobChatRequest {
    @NotNull @Positive private Long applicationId;
    @NotNull @Positive private Long vacancyId;
    @NotNull @Positive private Long candidateId;
    @NotNull @Positive private Long companyId;
}