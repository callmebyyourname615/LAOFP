package com.example.switching.connector;

import com.example.switching.outbox.dto.BankDispatchResult;
import com.example.switching.outbox.dto.BankIsoDispatchResponse;
import com.example.switching.outbox.dto.DispatchIsoMessageCommand;
import com.example.switching.outbox.dto.DispatchTransferCommand;

public interface BankConnector {

    BankDispatchResult dispatch(DispatchTransferCommand command);

    BankDispatchResult dispatchIsoMessage(DispatchIsoMessageCommand command);

    BankIsoDispatchResponse dispatchIsoMessageWithPacs002(DispatchIsoMessageCommand command);
}