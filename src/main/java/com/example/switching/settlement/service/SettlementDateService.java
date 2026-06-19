package com.example.switching.settlement.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class SettlementDateService {

    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate cursor = date.plusDays(1);
        while (isWeekend(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    public LocalDate previousBusinessDay(LocalDate date) {
        LocalDate cursor = date.minusDays(1);
        while (isWeekend(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
