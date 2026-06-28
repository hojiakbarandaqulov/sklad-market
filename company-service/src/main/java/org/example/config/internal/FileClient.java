package org.example.config.internal;

import org.example.dto.ApiResponse;
import org.example.dto.attach.AttachDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service")
public interface FileClient {

    @PostMapping(
            value = "/api/v1/attach/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    ApiResponse<AttachDto> uploadFile(@RequestParam("file") MultipartFile file);

}
