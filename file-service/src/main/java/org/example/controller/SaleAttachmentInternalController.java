package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.enums.AppLanguage;
import org.example.service.AttachService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/internal/attach")
public class SaleAttachmentInternalController {
    private final AttachService service;
    @GetMapping("/{id}/sale-info") @PreAuthorize("hasRole('SELLER')")
    public Info info(@PathVariable String id) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var attach=service.get(id,AppLanguage.UZ);
        if (!(auth instanceof JwtAuthenticationToken jwt) || Boolean.TRUE.equals(attach.getDeleted())
                || attach.getCreatedBy()==null || !attach.getCreatedBy().toString().equals(String.valueOf(jwt.getToken().getClaims().get("profileId"))))
            throw new AccessDeniedException("Attachment owner required");
        return new Info(attach.getId(),attach.getSize(),attach.getMimeType());
    }
    public record Info(String id, Long size, String mimeType) {}
}
