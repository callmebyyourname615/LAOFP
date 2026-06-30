package com.example.switching.connector;

import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.DispatchTransferCommand;
import com.example.switching.outbox.dto.StatusEnquiryCommand;
import com.example.switching.outbox.dto.StatusEnquiryResult;

public interface BankConnector {

    BankDispatchResult dispatch(DispatchTransferCommand command);

    BankDispatchResult dispatchIsoMessage(DispatchIsoMessageCommand command);

    BankIsoDispatchResponse dispatchIsoMessageWithPacs002(DispatchIsoMessageCommand command);

    default StatusEnquiryResult enquireStatus(StatusEnquiryCommand command) {
        return StatusEnquiryResult.unknown(
                "STATUS-ENQUIRY-NOT-SUPPORTED",
                "Connector does not support status enquiry");
    }
}
