package com.example.switching.crossborder.dto;
import java.time.Instant;
public record RailInstructionEvent(String rail,String externalReference,String internalReference,String eventType,String status,Instant receivedAt) {}
