package com.example.switching.liquidity.dto;

public record PoolTopUpResponse(
        Long topUpId,
        String reference,
        String status,
        PoolBalance balance) {
}
