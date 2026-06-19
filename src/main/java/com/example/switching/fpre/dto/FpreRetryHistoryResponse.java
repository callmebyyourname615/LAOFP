package com.example.switching.fpre.dto;

import java.util.List;

public record FpreRetryHistoryResponse(
        String txnId,
        int count,
        List<FpreRetryHistoryItemResponse> items) {
}
