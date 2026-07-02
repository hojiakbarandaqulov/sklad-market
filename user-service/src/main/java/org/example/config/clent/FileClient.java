package org.example.config.clent;

import org.example.dto.ApiResponse;
import org.example.dto.AttachDto;
import org.example.dto.internal.AttachInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service")
public interface FileClient {

    @GetMapping("/internal/attach/getById/{id}")
    AttachInfoDto getById(@PathVariable String id);

    @DeleteMapping("/api/v1/attach/delete/{id}")
    ApiResponse<Boolean> delete(@PathVariable String id,
                                @RequestHeader("Accept-Language") String language);

    @PostMapping(
            value = "/api/v1/attach/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AttachDto> upload(@RequestPart(value = "file") MultipartFile file,
                                  @RequestHeader("Accept-Language") String language);
}
