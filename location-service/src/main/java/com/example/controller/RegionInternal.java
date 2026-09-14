package com.example.controller;

import com.example.service.RegionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("api/v1/internal/region")
public class RegionInternal {

    private final RegionService regionService;

    @GetMapping("exists/{regionId}")
    public boolean existsByRegion(@PathVariable Long regionId){
        return regionService.existsByRegion(regionId);
    }
}
