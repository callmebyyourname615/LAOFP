package com.example.switching.capacity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CapacityGovernanceServiceTest {
    @Test void forecastsIncreasingSeries(){
        OffsetDateTime start=OffsetDateTime.parse("2026-06-01T00:00:00Z");
        List<CapacityGovernanceService.Observation> rows=java.util.stream.IntStream.range(0,6)
                .mapToObj(i->new CapacityGovernanceService.Observation(start.plusHours(i),BigDecimal.valueOf(100+i*10L))).toList();
        assertTrue(CapacityGovernanceService.linearForecast(rows,24).compareTo(new BigDecimal("300"))>0);
    }
    @Test void flatSeriesRemainsFlat(){
        OffsetDateTime start=OffsetDateTime.parse("2026-06-01T00:00:00Z");
        List<CapacityGovernanceService.Observation> rows=java.util.stream.IntStream.range(0,6)
                .mapToObj(i->new CapacityGovernanceService.Observation(start.plusHours(i),new BigDecimal("100"))).toList();
        assertEquals(new BigDecimal("100.0000"),CapacityGovernanceService.linearForecast(rows,24));
    }
}
