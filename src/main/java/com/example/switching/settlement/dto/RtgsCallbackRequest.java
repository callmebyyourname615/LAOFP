package com.example.switching.settlement.dto;

public record RtgsCallbackRequest(
        String instructionRef,
        String rtgsMsgId,
        String status,
        String reason
) {}
