package com.example.switching.crossborder.adapter;

import com.example.switching.crossborder.dto.CrossBorderInitiateRequest;
import com.example.switching.crossborder.entity.FxQuoteEntity;

/**
 * Strategy interface for cross-border network adapters.
 *
 * <p>Each implementation handles one target network (PromptPay, CNAPS, NAPAS, SWIFT).
 * The {@link #targetNetwork()} value must match the {@code fx_corridors.target_network} column.
 */
public interface CrossBorderNetworkAdapter {

    /** Network identifier — must match fx_corridors.target_network. */
    String targetNetwork();

    /**
     * Send the cross-border payment instruction to the target network.
     *
     * @return the network-assigned transaction ID (networkTxnId)
     * @throws com.example.switching.crossborder.exception.CorridorNotAvailableException
     *         if the network rejects the request or is unreachable
     */
    String send(CrossBorderInitiateRequest request, FxQuoteEntity quote, Long cbId);
}
