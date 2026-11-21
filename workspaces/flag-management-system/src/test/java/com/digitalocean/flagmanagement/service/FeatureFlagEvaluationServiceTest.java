package com.digitalocean.flagmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.digitalocean.flagmanagement.cache.CachedEvaluation;
import com.digitalocean.flagmanagement.cache.CachedFlagConfig;
import com.digitalocean.flagmanagement.cache.FeatureFlagCacheService;
import com.digitalocean.flagmanagement.dto.EvaluateRequest;
import com.digitalocean.flagmanagement.dto.EvaluateResponse;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.entity.FeatureFlagOverride;
import com.digitalocean.flagmanagement.exception.ResourceNotFoundException;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;

@ExtendWith(MockitoExtension.class)
class FeatureFlagEvaluationServiceTest {

    private static final String FLAG_NAME = "new_checkout";
    private static final String USER_ID = "user123";
    private static final UUID FLAG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long FLAG_VERSION = 1L;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private FeatureFlagOverrideRepository overrideRepository;

    @Mock
    private FeatureFlagCacheService cacheService;

    @InjectMocks
    private FeatureFlagEvaluationService evaluationService;

    private FeatureFlag flag;

    @BeforeEach
    void setUp() {
        flag = new FeatureFlag(FLAG_NAME, "New checkout flow", true);
        setFlagId(flag, FLAG_ID);
        lenient().when(cacheService.getFlagConfig(anyString())).thenReturn(Optional.empty());
        lenient().when(cacheService.getEvaluation(any(), anyLong(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void evaluate_globalEnabled_noOverride_returnsGlobalTrue() {
        flag.setEnabled(true);
        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.empty());

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.flagName()).isEqualTo(FLAG_NAME);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.enabled()).isTrue();
        assertThat(response.reason()).isEqualTo("GLOBAL");
    }

    @Test
    void evaluate_globalDisabled_noOverride_returnsGlobalFalse() {
        flag.setEnabled(false);
        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.empty());

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.enabled()).isFalse();
        assertThat(response.reason()).isEqualTo("GLOBAL");
    }

    @Test
    void evaluate_globalEnabled_userOverrideFalse_returnsOverrideFalse() {
        flag.setEnabled(true);
        FeatureFlagOverride override = new FeatureFlagOverride(FLAG_ID, USER_ID, false);

        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.of(override));

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.enabled()).isFalse();
        assertThat(response.reason()).isEqualTo("USER_OVERRIDE");
    }

    @Test
    void evaluate_globalDisabled_userOverrideTrue_returnsOverrideTrue() {
        flag.setEnabled(false);
        FeatureFlagOverride override = new FeatureFlagOverride(FLAG_ID, USER_ID, true);

        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.of(override));

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.enabled()).isTrue();
        assertThat(response.reason()).isEqualTo("USER_OVERRIDE");
    }

    @Test
    void evaluate_missingFlag_throwsNotFound() {
        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(FLAG_NAME);

        verifyNoInteractions(overrideRepository);
        verify(cacheService, never()).putFlagConfig(any());
        verify(cacheService, never()).putEvaluation(any(), anyLong(), anyString(), any());
    }

    @Test
    void evaluate_missingOverrideVsExplicitFalse_areDistinct() {
        flag.setEnabled(true);
        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));

        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.empty());
        EvaluateResponse missingOverride = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        FeatureFlagOverride explicitFalse = new FeatureFlagOverride(FLAG_ID, USER_ID, false);
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.of(explicitFalse));
        EvaluateResponse explicitOverride = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(missingOverride.enabled()).isTrue();
        assertThat(missingOverride.reason()).isEqualTo("GLOBAL");

        assertThat(explicitOverride.enabled()).isFalse();
        assertThat(explicitOverride.reason()).isEqualTo("USER_OVERRIDE");
    }

    @Test
    void evaluate_cacheHit_returnsCachedEvaluationWithoutDbLookup() {
        CachedFlagConfig cachedFlag = new CachedFlagConfig(FLAG_ID, FLAG_NAME, true, FLAG_VERSION);
        CachedEvaluation cachedEval = new CachedEvaluation(false, "USER_OVERRIDE");

        when(cacheService.getFlagConfig(FLAG_NAME)).thenReturn(Optional.of(cachedFlag));
        when(cacheService.getEvaluation(FLAG_ID, FLAG_VERSION, USER_ID)).thenReturn(Optional.of(cachedEval));

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.enabled()).isFalse();
        assertThat(response.reason()).isEqualTo("USER_OVERRIDE");
        verifyNoInteractions(featureFlagRepository);
        verifyNoInteractions(overrideRepository);
        verify(cacheService, never()).putFlagConfig(any());
        verify(cacheService, never()).putEvaluation(any(), anyLong(), anyString(), any());
    }

    @Test
    void evaluate_cacheMiss_loadsFromDbAndPopulatesCache() {
        when(featureFlagRepository.findByName(FLAG_NAME)).thenReturn(Optional.of(flag));
        when(overrideRepository.findByFlagIdAndUserId(FLAG_ID, USER_ID)).thenReturn(Optional.empty());

        EvaluateResponse response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(response.enabled()).isTrue();
        assertThat(response.reason()).isEqualTo("GLOBAL");

        verify(cacheService).putFlagConfig(eq(new CachedFlagConfig(FLAG_ID, FLAG_NAME, true, FLAG_VERSION)));
        verify(cacheService).putEvaluation(
                eq(FLAG_ID),
                eq(FLAG_VERSION),
                eq(USER_ID),
                eq(new CachedEvaluation(true, "GLOBAL"))
        );
    }

    private static void setFlagId(FeatureFlag featureFlag, UUID id) {
        try {
            var field = FeatureFlag.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(featureFlag, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set FeatureFlag id for test", e);
        }
    }
}
