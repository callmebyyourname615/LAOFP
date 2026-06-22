package com.example.switching.crossborder.dto;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.Map;
public record RailInstruction(String internalRef,String externalRef,String sourceParticipant,String destinationParticipant,
        String sourceCurrency,String destinationCurrency,BigDecimal sourceAmount,BigDecimal destinationAmount,
        String beneficiaryAccount,String beneficiaryName,String purposeCode,LocalDate settlementDate,Map<String,Object> attributes) {}
