package com.example.switching.liquidity.dto;

import java.util.List;

import com.example.switching.liquidity.service.PoolService.PoolTransactionHistoryItem;

public record PoolHistoryResponse(
        String pspId,
        List<PoolTransactionHistoryItem> items) {
}
