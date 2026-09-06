package com.undertow.reconciliation.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signal_reconciliations")
public class SignalReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "checkin_id", nullable = false)
    private UUID checkinId;

    @Column(name = "signal_ledger_entry_id", nullable = false)
    private UUID signalLedgerEntryId;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "severity_before")
    private Integer severityBefore;

    @Column(name = "severity_after")
    private Integer severityAfter;

    @Column(name = "narrative_text")
    private String narrativeText;

    protected SignalReconciliation() {
        // JPA
    }

    public SignalReconciliation(UUID checkinId, UUID signalLedgerEntryId, String outcome,
                                 Integer severityBefore, Integer severityAfter, String narrativeText) {
        this.checkinId = checkinId;
        this.signalLedgerEntryId = signalLedgerEntryId;
        this.outcome = outcome;
        this.severityBefore = severityBefore;
        this.severityAfter = severityAfter;
        this.narrativeText = narrativeText;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckinId() {
        return checkinId;
    }

    public UUID getSignalLedgerEntryId() {
        return signalLedgerEntryId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Integer getSeverityBefore() {
        return severityBefore;
    }

    public Integer getSeverityAfter() {
        return severityAfter;
    }

    public String getNarrativeText() {
        return narrativeText;
    }
}
