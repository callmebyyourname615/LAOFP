package com.example.switching.aml.dto;

/**
 * Result returned by {@link com.example.switching.aml.service.SanctionsScreeningService#screen}.
 */
public class ScreeningResult {

    /** CLEAR | BLOCKED | MANUAL_REVIEW */
    private final String outcome;

    /** Name of the matched entity (null if CLEAR). */
    private final String matchEntity;

    /** Source list that triggered the match: BOL | OFAC | UN (null if CLEAR). */
    private final String listType;

    /** Match confidence 0–100, or null if no match. */
    private final Double matchScore;

    /** Wall-clock time of the screening query in ms. */
    private final long screeningMs;

    public ScreeningResult(String outcome, String matchEntity, String listType,
                           Double matchScore, long screeningMs) {
        this.outcome = outcome;
        this.matchEntity = matchEntity;
        this.listType = listType;
        this.matchScore = matchScore;
        this.screeningMs = screeningMs;
    }

    public static ScreeningResult clear(long screeningMs) {
        return new ScreeningResult("CLEAR", null, null, null, screeningMs);
    }

    public static ScreeningResult blocked(String matchEntity, String listType,
                                          double matchScore, long screeningMs) {
        return new ScreeningResult("BLOCKED", matchEntity, listType, matchScore, screeningMs);
    }

    public static ScreeningResult manualReview(String matchEntity, String listType,
                                               double matchScore, long screeningMs) {
        return new ScreeningResult("MANUAL_REVIEW", matchEntity, listType, matchScore, screeningMs);
    }

    public String getOutcome()      { return outcome; }
    public String getMatchEntity()  { return matchEntity; }
    public String getListType()     { return listType; }
    public Double getMatchScore()   { return matchScore; }
    public long   getScreeningMs()  { return screeningMs; }

    public boolean isBlocked()       { return "BLOCKED".equals(outcome); }
    public boolean isManualReview()  { return "MANUAL_REVIEW".equals(outcome); }
    public boolean isClear()         { return "CLEAR".equals(outcome); }
}
