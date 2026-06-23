package com.example.switching.consistency;

/** Explicit database-read semantics. Replica use is never implicit. */
public enum ReadConsistency {
    STRICT_PRIMARY,
    READ_YOUR_WRITES,
    EVENTUAL
}
