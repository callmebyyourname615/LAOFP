package com.example.switching.demo.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;

import com.example.switching.demo.dto.DemoRequestFlowResponse;
import com.example.switching.demo.dto.DemoResponseFlowResponse;
import com.example.switching.demo.dto.TransferTraceResponse;

@Service
public class DemoFlowService {

    public DemoRequestFlowResponse buildRequestFlow(String transferRef,
                                                    String sourceBank,
                                                    String destinationBank,
                                                    String messageType,
                                                    String payload) {
        DemoRequestFlowResponse response = new DemoRequestFlowResponse();
        response.setTransferRef(transferRef);
        response.setSourceBank(sourceBank);
        response.setDestinationBank(destinationBank);
        response.setMessageType(messageType);
        response.setRequestPayload(payload);
        response.setSwitchingStatus("RECEIVED");
        response.setRoutingResult(destinationBank);
        response.setBankBStatus("PENDING");
        return response;
    }

    public DemoResponseFlowResponse buildResponseFlow(String transferRef,
                                                      String sourceBank,
                                                      String destinationBank,
                                                      String messageType,
                                                      String payload) {
        DemoResponseFlowResponse response = new DemoResponseFlowResponse();
        response.setTransferRef(transferRef);
        response.setSourceBank(sourceBank);
        response.setDestinationBank(destinationBank);
        response.setMessageType(messageType);
        response.setResponsePayload(payload);
        response.setSwitchingStatus("UPDATED");
        response.setBankAStatus("DELIVERED");
        response.setTransferStatus("COMPLETED");
        return response;
    }

    public TransferTraceResponse buildTrace(String transferRef) {
        TransferTraceResponse response = new TransferTraceResponse();
        response.setTransferRef(transferRef);
        response.setCurrentStatus("IN_PROGRESS");
        response.setSourceBank("BANK_A");
        response.setDestinationBank("BANK_B");
        response.setTimeline(Arrays.asList(
                "Bank A sent request",
                "Switching received message",
                "Routing resolved to Bank B",
                "Outbox event created",
                "Dispatch pending"
        ));
        return response;
    }
}