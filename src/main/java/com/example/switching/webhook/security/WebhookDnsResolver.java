package com.example.switching.webhook.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@FunctionalInterface
public interface WebhookDnsResolver {
    List<InetAddress> resolve(String host) throws UnknownHostException;

    static WebhookDnsResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
