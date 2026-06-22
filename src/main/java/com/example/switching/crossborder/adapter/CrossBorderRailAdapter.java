package com.example.switching.crossborder.adapter;
import com.example.switching.crossborder.dto.RailInstruction;import com.example.switching.crossborder.dto.RailInstructionEvent;import com.example.switching.crossborder.dto.RailTransactionRef;
public interface CrossBorderRailAdapter { String rail(); RailTransactionRef submit(RailInstruction instruction); RailInstructionEvent acceptInbound(String externalRef,String messageType,String payload,String signature); }
