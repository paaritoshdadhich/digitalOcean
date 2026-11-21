package com.digitalocean.flagmanagement.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalocean.flagmanagement.dto.EvaluateRequest;
import com.digitalocean.flagmanagement.dto.EvaluateResponse;
import com.digitalocean.flagmanagement.service.FeatureFlagEvaluationService;

@RestController
@RequestMapping("/api/v1/evaluate")
public class FeatureFlagEvaluationController {

    private final FeatureFlagEvaluationService evaluationService;

    public FeatureFlagEvaluationController(FeatureFlagEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluateResponse evaluate(@Valid @RequestBody EvaluateRequest request) {
        return evaluationService.evaluate(request);
    }
}
