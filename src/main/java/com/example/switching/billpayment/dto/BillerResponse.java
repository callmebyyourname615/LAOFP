package com.example.switching.billpayment.dto;

public record BillerResponse(
        Long   billerId,
        String billerCode,
        String billerName,
        String category,
        String status
) {}
