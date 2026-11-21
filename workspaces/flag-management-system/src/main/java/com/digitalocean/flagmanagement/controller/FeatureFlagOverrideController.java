package com.digitalocean.flagmanagement.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.digitalocean.flagmanagement.dto.FeatureFlagOverrideResponse;
import com.digitalocean.flagmanagement.dto.UpsertOverrideRequest;
import com.digitalocean.flagmanagement.service.FeatureFlagOverrideService;

@RestController
@RequestMapping("/api/v1/flags/{name}/overrides/{userId}")
public class FeatureFlagOverrideController {

    private final FeatureFlagOverrideService overrideService;

    public FeatureFlagOverrideController(FeatureFlagOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    @PutMapping
    public FeatureFlagOverrideResponse upsert(
            @PathVariable String name,
            @PathVariable String userId,
            @Valid @RequestBody UpsertOverrideRequest request,
            @RequestHeader(value = FeatureFlagController.ACTOR_HEADER, defaultValue = "anonymous")
            String actorId
    ) {
        return overrideService.upsert(name, userId, request, actorId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String name,
            @PathVariable String userId,
            @RequestHeader(value = FeatureFlagController.ACTOR_HEADER, defaultValue = "anonymous")
            String actorId
    ) {
        overrideService.delete(name, userId, actorId);
    }
}
