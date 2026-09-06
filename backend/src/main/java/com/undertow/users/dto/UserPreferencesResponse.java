package com.undertow.users.dto;

import com.undertow.users.entity.UserPreferences;

public record UserPreferencesResponse(
        int persistenceThreshold,
        int decouplingThresholdDelta,
        int silenceThresholdDelta,
        int abnormalityThresholdDelta,
        String notificationPref
) {
    public static UserPreferencesResponse from(UserPreferences p) {
        return new UserPreferencesResponse(
                p.getPersistenceThreshold(), p.getDecouplingThresholdDelta(),
                p.getSilenceThresholdDelta(), p.getAbnormalityThresholdDelta(), p.getNotificationPref());
    }
}
