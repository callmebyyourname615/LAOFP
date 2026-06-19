package com.example.switching.fpre.dto;

import java.util.List;

public record FpreTransferListResponse(
        int count,
        int limit,
        String pspId,
        List<FpreTransferItemResponse> items) {
}
