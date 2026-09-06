package com.undertow.trust.service;

import com.undertow.trust.model.TrustStatus;

public record TrustAssessment(TrustStatus status, double confidence, String explanation) {
}
