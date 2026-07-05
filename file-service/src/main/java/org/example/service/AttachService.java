package org.example.service;

import org.example.dto.AttachDto;
import org.example.dto.AttachInfoDto;
import org.example.entity.Attach;
import org.example.enums.AppLanguage;
import org.springframework.web.multipart.MultipartFile;

public interface AttachService {


    AttachDto uploadFile(MultipartFile file, AppLanguage language);

    boolean delete(String id, AppLanguage language);

    Attach get(String id, AppLanguage language);

    AttachInfoDto getInfoAttach(String id);
    byte[] open(String fileId, AppLanguage language);
}
