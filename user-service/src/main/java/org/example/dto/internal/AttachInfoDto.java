package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachInfoDto {
    private String id;
    private String originalName;
    private Long size;
    private String extension;
    private String path;
    private String mimeType;
}
