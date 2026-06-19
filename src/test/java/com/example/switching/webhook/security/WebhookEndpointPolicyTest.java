package com.example.switching.webhook.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WebhookEndpointPolicyTest {
    @Test void acceptsPublicHttpsAllowlistedHost() throws Exception {
        var p = strict(); p.setAllowedHosts(List.of("*.bank.example"));
        var policy = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getByName("8.8.8.8")));
        assertDoesNotThrow(() -> policy.validate("https://hooks.bank.example/events"));
    }
    @Test void rejectsLoopbackAndPrivateDnsAnswers() throws Exception {
        var p = strict(); p.setAllowedHosts(List.of("hooks.bank.example"));
        var loopback = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getByName("127.0.0.1")));
        var privateIp = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getByName("10.10.0.2")));
        assertThrows(WebhookEndpointRejectedException.class, () -> loopback.validate("https://hooks.bank.example/a"));
        assertThrows(WebhookEndpointRejectedException.class, () -> privateIp.validate("https://hooks.bank.example/a"));
    }
    @Test void rejectsMetadataHttpUserInfoAndNonAllowlistedHost() {
        var p = strict(); p.setAllowedHosts(List.of("hooks.bank.example"));
        var policy = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getLoopbackAddress()));
        assertThrows(WebhookEndpointRejectedException.class, () -> policy.validate("http://169.254.169.254/latest/meta-data"));
        assertThrows(WebhookEndpointRejectedException.class, () -> policy.validate("https://user:pass@hooks.bank.example/a"));
        assertThrows(WebhookEndpointRejectedException.class, () -> policy.validate("https://evil.example/a"));
    }
    @Test void validatesEveryDnsAnswerToReduceRebindingRisk() throws Exception {
        var p = strict(); p.setAllowedHosts(List.of("hooks.bank.example"));
        var policy = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("192.168.1.2")));
        assertThrows(WebhookEndpointRejectedException.class, () -> policy.validate("https://hooks.bank.example/a"));
    }

    @Test void rejectsDocumentationAndBenchmarkReservedRanges() throws Exception {
        var p = strict(); p.setAllowedHosts(List.of("hooks.bank.example"));
        for (String address : List.of("192.0.2.10", "198.51.100.10", "203.0.113.10", "198.18.0.1")) {
            var policy = new WebhookEndpointPolicy(p, h -> List.of(InetAddress.getByName(address)));
            assertThrows(WebhookEndpointRejectedException.class,
                    () -> policy.validate("https://hooks.bank.example/a"));
        }
    }
    private WebhookEndpointPolicyProperties strict() {
        var p = new WebhookEndpointPolicyProperties(); p.setRequireHttps(true); p.setRequireAllowlist(true); p.setAllowedPorts(Set.of(443)); return p;
    }
}
