package com.example.switching.common.error;

public enum ErrorPhase {
    RECEIVE_REQUEST,
    VALIDATE_REQUEST,
    LOOKUP_INQUIRY,
    CREATE_TRANSFER,
    ENQUEUE_OUTBOX,
    DISPATCH_TRANSFER,
    READ_RESOURCE,
    WRITE_DATABASE,
    PARSE_PAYLOAD,
    UNKNOWN
}