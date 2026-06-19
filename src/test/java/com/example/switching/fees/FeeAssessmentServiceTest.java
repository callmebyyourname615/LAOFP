package com.example.switching.fees;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
class FeeAssessmentServiceTest {
 @Test void appliesRateFlatMinimumAndMaximum(){assertEquals(new BigDecimal("15.0000"),FeeAssessmentService.calculate(new BigDecimal("1000"),new BigDecimal("5"),new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("20")));}
 @Test void appliesMinimum(){assertEquals(new BigDecimal("2.0000"),FeeAssessmentService.calculate(new BigDecimal("1"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("2"),null));}
}
