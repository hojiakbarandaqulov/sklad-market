package org.example.dto.internal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileSummaryResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String photoUrl;
}
