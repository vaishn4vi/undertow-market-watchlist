package com.undertow.users.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "persistence_threshold", nullable = false)
    private int persistenceThreshold;

    @Column(name = "decoupling_threshold_delta", nullable = false)
    private int decouplingThresholdDelta;

    @Column(name = "silence_threshold_delta", nullable = false)
    private int silenceThresholdDelta;

    @Column(name = "abnormality_threshold_delta", nullable = false)
    private int abnormalityThresholdDelta;

    @Column(name = "notification_pref", nullable = false)
    private String notificationPref;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreferences() {
        // JPA
    }

    public UserPreferences(UUID userId) {
        this.userId = userId;
        this.persistenceThreshold = 70;
        this.decouplingThresholdDelta = 0;
        this.silenceThresholdDelta = 0;
        this.abnormalityThresholdDelta = 0;
        this.notificationPref = "IN_APP";
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getPersistenceThreshold() {
        return persistenceThreshold;
    }

    public void setPersistenceThreshold(int persistenceThreshold) {
        this.persistenceThreshold = persistenceThreshold;
        touch();
    }

    public int getDecouplingThresholdDelta() {
        return decouplingThresholdDelta;
    }

    public void setDecouplingThresholdDelta(int decouplingThresholdDelta) {
        this.decouplingThresholdDelta = decouplingThresholdDelta;
        touch();
    }

    public int getSilenceThresholdDelta() {
        return silenceThresholdDelta;
    }

    public void setSilenceThresholdDelta(int silenceThresholdDelta) {
        this.silenceThresholdDelta = silenceThresholdDelta;
        touch();
    }

    public int getAbnormalityThresholdDelta() {
        return abnormalityThresholdDelta;
    }

    public void setAbnormalityThresholdDelta(int abnormalityThresholdDelta) {
        this.abnormalityThresholdDelta = abnormalityThresholdDelta;
        touch();
    }

    public String getNotificationPref() {
        return notificationPref;
    }

    public void setNotificationPref(String notificationPref) {
        this.notificationPref = notificationPref;
        touch();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
