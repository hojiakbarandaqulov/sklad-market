package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.AttachInfoDto;
import org.example.entity.Attach;
import org.example.service.AttachService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/attach")
public class AttachInternalController {

    private final AttachService attachService;

    @GetMapping("/getById/{id}")
    public AttachInfoDto getById(@PathVariable String id) {
        return attachService.getInfoAttach(id);
    }
}

