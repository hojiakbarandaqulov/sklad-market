package org.example.dto.users;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.Roles;

@Getter
@Setter
public class UserContextResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private Roles role;
    private String photoUrl;
    private Long companyId;
    private String companyName;
    private String companyLogoUrl;
    private Boolean sellerPanel;
    private Boolean moderatorPanel;
    private Boolean companyProfile;
}
