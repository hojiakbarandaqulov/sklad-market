package org.example.controller;

import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.example.dto.AttachInfoDto;
import org.example.entity.Attach;
import org.example.enums.AppLanguage;
import org.example.service.AttachService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/attach")
public class AttachInternalController {

    private final AttachService attachService;

    @GetMapping("/getById/{id:.+}")
    public AttachInfoDto getById(@PathVariable String id) {
        return attachService.getInfoAttach(id);
    }

    @PermitAll
    @GetMapping("/open/{id:.+}")
    public ResponseEntity<byte[]> open(@PathVariable String id) {
        byte[] fileData = attachService.open(id, AppLanguage.UZ);
        Attach attach = attachService.get(id,AppLanguage.UZ);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attach.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(attach.getMimeType()))
                .body(fileData);
    }
}

