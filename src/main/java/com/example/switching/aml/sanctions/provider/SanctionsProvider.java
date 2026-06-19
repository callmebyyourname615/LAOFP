package com.example.switching.aml.sanctions.provider;

import com.example.switching.aml.sanctions.model.SanctionsSnapshot;

/** External sanctions source adapter. Implementations must return a complete snapshot. */
public interface SanctionsProvider {
    String providerCode();
    boolean enabled();
    int minimumRecords();
    SanctionsSnapshot fetchSnapshot();
}
