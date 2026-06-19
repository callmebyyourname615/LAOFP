package com.example.switching.reconciliation.dto;

import java.util.List;

public record ReconDiscrepancyReport(
        String fileRef,
        int totalRecords,
        int matchedCount,
        int unmatchedCount,
        int disputedCount,
        List<ReconItemResponse> discrepancies
) {}
