package org.example.ai.provider;

/** One inline image for a vision-capable generation call. {@code mimeType} must be an {@code image/*} type. */
public record ImagePart(byte[] data, String mimeType) {
}
