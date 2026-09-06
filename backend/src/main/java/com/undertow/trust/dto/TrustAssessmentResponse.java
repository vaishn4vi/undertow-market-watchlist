package com.undertow.trust.dto;

import com.undertow.trust.service.TrustAssessment;

public record TrustAssessmentResponse(String status, double confidence, String explanation) {
    public static TrustAssessmentResponse from(TrustAssessment assessment) {
        return new TrustAssessmentResponse(assessment.status().name(), assessment.confidence(), assessment.explanation());
    }

    public static TrustAssessmentResponse unknown() {
        return new TrustAssessmentResponse("UNKNOWN", 0.0, "No market data has ever been ingested for this symbol.");
    }
}
