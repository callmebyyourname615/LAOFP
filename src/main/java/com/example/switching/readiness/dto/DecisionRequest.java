package com.example.switching.readiness.dto;

public record DecisionRequest(String releaseCommit, String imageDigest, boolean canaryRequested) {
}
