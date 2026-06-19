package com.example.switching.fx;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
class GovernedFxRateServiceTest {
 @Test void calculatesOddAndEvenMedian(){assertEquals(new BigDecimal("2"),GovernedFxRateService.median(List.of(new BigDecimal("3"),new BigDecimal("1"),new BigDecimal("2"))));assertEquals(new BigDecimal("2.0000000000"),GovernedFxRateService.median(List.of(new BigDecimal("1"),new BigDecimal("3"))));}
}
