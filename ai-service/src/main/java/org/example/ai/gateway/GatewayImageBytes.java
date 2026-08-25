package org.example.ai.gateway;

/** Raw bytes + content-type from a binary GET (e.g. file-service's {@code /api/v1/attach/open/{id}}). */
public record GatewayImageBytes(byte[] data, String contentType) {
}
