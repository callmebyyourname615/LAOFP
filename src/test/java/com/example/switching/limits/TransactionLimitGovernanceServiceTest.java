package com.example.switching.limits;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionLimitGovernanceServiceTest {
    @Test void createsHourAndDayWindowsInPolicyTimezone(){
        OffsetDateTime input=OffsetDateTime.parse("2026-06-19T17:45:12Z");
        ZoneId zone=ZoneId.of("Asia/Vientiane");
        var hour=TransactionLimitGovernanceService.hourWindow(input,zone);
        assertEquals(0,hour.start().getMinute());
        assertEquals(Duration.ofHours(1),Duration.between(hour.start(),hour.end()));
        var day=TransactionLimitGovernanceService.dayWindow(input,zone);
        assertEquals(LocalTime.MIDNIGHT,day.start().toLocalTime());
        assertEquals(Duration.ofDays(1),Duration.between(day.start(),day.end()));
    }
}
