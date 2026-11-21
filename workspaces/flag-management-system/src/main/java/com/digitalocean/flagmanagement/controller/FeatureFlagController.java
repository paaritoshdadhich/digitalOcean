package com.digitalocean.flagmanagement.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalocean.flagmanagement.dto.CreateFeatureFlagRequest;
import com.digitalocean.flagmanagement.dto.FeatureFlagResponse;
import com.digitalocean.flagmanagement.dto.UpdateFeatureFlagRequest;
import com.digitalocean.flagmanagement.service.FeatureFlagService;

@RestController
@RequestMapping("/api/v1/flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @PostMapping
    public ResponseEntity<FeatureFlagResponse> create(
            @Valid @RequestBody CreateFeatureFlagRequest request
    ) {
        FeatureFlagResponse created = featureFlagService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<FeatureFlagResponse> list() {
        return featureFlagService.findAll();
    }

    @GetMapping("/{name}")
    public FeatureFlagResponse getByName(@PathVariable String name) {
        return featureFlagService.findByName(name);
    }

    @PutMapping("/{name}")
    public FeatureFlagResponse update(
            @PathVariable String name,
            @Valid @RequestBody UpdateFeatureFlagRequest request
    ) {
        return featureFlagService.update(name, request);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        featureFlagService.delete(name);
    }
}
