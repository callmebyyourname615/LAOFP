package com.example.switching.readiness.dto;

import java.util.List;

public record LedgerValidation(boolean valid, int entryCount, String tailHash, List<String> errors) {
}
