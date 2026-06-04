package org.example.service;

import org.example.dto.AttachDto;
import org.springframework.web.multipart.MultipartFile;

public interface AttachService {

    AttachDto upload(MultipartFile file);
}
