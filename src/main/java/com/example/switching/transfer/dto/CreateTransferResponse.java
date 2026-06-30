package com.example.switching.transfer.dto;

public class CreateTransferResponse {

    private String transferRef;
    private String status;
    private String result;
    private String resultDetail;
    private String message;

    public CreateTransferResponse() {
    }

    public CreateTransferResponse(String transferRef, String status, String message) {
        this.transferRef = transferRef;
        this.status = status;
        this.result = mapResult(status);
        this.resultDetail = mapResultDetail(status);
        this.message = message;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResultDetail() {
        return resultDetail;
    }

    public void setResultDetail(String resultDetail) {
        this.resultDetail = resultDetail;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private static String mapResult(String status) {
        if ("REJECTED".equals(status) || "FAILED".equals(status)) {
            return "FAILED";
        }
        return "OK";
    }

    private static String mapResultDetail(String status) {
        if ("ACCEPTED".equals(status)) {
            return "PENDING";
        }
        if ("READY_FOR_SETTLEMENT".equals(status) || "SETTLED".equals(status)) {
            return "OK";
        }
        if ("REJECTED".equals(status)) {
            return "REJECTED";
        }
        if ("DRS_REQUIRED".equals(status)) {
            return "DRS_REQUIRED";
        }
        if ("FAILED".equals(status)) {
            return "FAILED";
        }
        return status;
    }
}
