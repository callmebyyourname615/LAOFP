package com.example.switching.finality;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentFinalityServiceTest {
    @Test
    void duplicateFingerprintIsCanonicalAndStable(){
        String accountA="a".repeat(64); String accountB="b".repeat(64);
        String first=PaymentFinalityService.duplicateFingerprint(" bank01 ","transfer",accountA,accountB,new BigDecimal("100.00"),"lak",LocalDate.of(2026,6,19),42);
        String second=PaymentFinalityService.duplicateFingerprint("BANK01","TRANSFER",accountA,accountB,new BigDecimal("100"),"LAK",LocalDate.of(2026,6,19),42);
        assertEquals(first,second);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }
}
