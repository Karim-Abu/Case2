package com.example.droolsengine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA Entity für das Entscheidungsprotokoll.
 *
 * Jeder Datensatz ist unveränderlich (kein Update nach dem INSERT).
 * Pro Prozessinstanz können mehrere Einträge existieren, verknüpft über
 * processInstanceId:
 * - Eintrag 1: decisionSource=DROOLS, decisionStatus=MANUAL_REVIEW (wenn Drools
 * "Mensch nötig" sagt)
 * - Eintrag 2: decisionSource=HUMAN, decisionStatus=FINAL (was der Mensch
 * tatsächlich entschied)
 *
 * Dieses Design ermöglicht ML-Training:
 * "Features": deliveryCountry + weight
 * "Label": deliveryType des FINAL-Eintrags
 * "Signal": Übersteuerungsrate (Anteil Manual an allen Entscheidungen)
 */
@Entity
@Table(name = "decision_log")
public class DecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISO 3166 A-2 Ländercode, z.B. "AR", "JP", "RU". */
    @Column(name = "delivery_country", nullable = false, length = 10)
    private String deliveryCountry;

    /** Gewicht der Sendung in kg. */
    @Column(nullable = false)
    private double weight;

    /**
     * Versandart-Entscheidung.
     * Für DROOLS-Einträge: die von der Rule Engine bestimmte Art (inkl.
     * MANUAL_REVIEW).
     * Für HUMAN-Einträge: die tatsächlich gewählte Art des Sachbearbeiters.
     */
    @Column(name = "delivery_type", nullable = false, length = 30)
    private String deliveryType;

    /** Wer hat diese Entscheidung getroffen? DROOLS oder HUMAN. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_source", nullable = false, length = 10)
    private DecisionSource decisionSource;

    /** Status der Entscheidung im Prozesskontext. Siehe DecisionStatus-Javadoc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 20)
    private DecisionStatus decisionStatus;

    /**
     * Name der gefeuerten Drools-Regel.
     * Null bei HUMAN-Einträgen.
     * "Delivery Fallback" → kein spezifischer Treffer (NO_RULE_MATCH-Fall).
     */
    @Column(name = "rule_name", length = 100)
    private String ruleName;

    /**
     * Version der Regelkonfiguration zum Zeitpunkt der Entscheidung.
     * Frei konfigurierbar via app.rule-version in application.properties.
     * Null bei HUMAN-Einträgen.
     */
    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    /**
     * Begründung der manuellen Überschreibung.
     * Null bei DROOLS-Einträgen.
     * Pflichtfeld bei HUMAN/FINAL-Einträgen (erzwungen im Service).
     */
    @Column(name = "manual_reason", length = 500)
    private String manualReason;

    /**
     * Camunda Prozessinstanz-ID.
     * Verknüpft DROOLS-Eintrag + HUMAN-Eintrag derselben Prozessinstanz.
     * Optional — wenn der Aufruf nicht durch Camunda erfolgt (z.B. direkter
     * REST-Test).
     */
    @Column(name = "process_instance_id", length = 100)
    private String processInstanceId;

    /** Wird automatisch gesetzt via @PrePersist. Nie manuell setzen. */
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    // --- Konstruktoren ---

    protected DecisionLog() {
        // JPA benötigt Default-Konstruktor
    }

    private DecisionLog(Builder builder) {
        this.deliveryCountry = builder.deliveryCountry;
        this.weight = builder.weight;
        this.deliveryType = builder.deliveryType;
        this.decisionSource = builder.decisionSource;
        this.decisionStatus = builder.decisionStatus;
        this.ruleName = builder.ruleName;
        this.ruleVersion = builder.ruleVersion;
        this.manualReason = builder.manualReason;
        this.processInstanceId = builder.processInstanceId;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String deliveryCountry;
        private double weight;
        private String deliveryType;
        private DecisionSource decisionSource;
        private DecisionStatus decisionStatus;
        private String ruleName;
        private String ruleVersion;
        private String manualReason;
        private String processInstanceId;

        public Builder deliveryCountry(String v) {
            this.deliveryCountry = v;
            return this;
        }

        public Builder weight(double v) {
            this.weight = v;
            return this;
        }

        public Builder deliveryType(String v) {
            this.deliveryType = v;
            return this;
        }

        public Builder decisionSource(DecisionSource v) {
            this.decisionSource = v;
            return this;
        }

        public Builder decisionStatus(DecisionStatus v) {
            this.decisionStatus = v;
            return this;
        }

        public Builder ruleName(String v) {
            this.ruleName = v;
            return this;
        }

        public Builder ruleVersion(String v) {
            this.ruleVersion = v;
            return this;
        }

        public Builder manualReason(String v) {
            this.manualReason = v;
            return this;
        }

        public Builder processInstanceId(String v) {
            this.processInstanceId = v;
            return this;
        }

        public DecisionLog build() {
            if (deliveryCountry == null || deliveryType == null
                    || decisionSource == null || decisionStatus == null) {
                throw new IllegalStateException(
                        "deliveryCountry, deliveryType, decisionSource und decisionStatus sind Pflichtfelder.");
            }
            return new DecisionLog(this);
        }
    }

    // --- Getter (kein Setter — Entity ist immutable nach dem Einmaligen Speichern)
    // ---

    public Long getId() {
        return id;
    }

    public String getDeliveryCountry() {
        return deliveryCountry;
    }

    public double getWeight() {
        return weight;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public DecisionSource getDecisionSource() {
        return decisionSource;
    }

    public DecisionStatus getDecisionStatus() {
        return decisionStatus;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getManualReason() {
        return manualReason;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
