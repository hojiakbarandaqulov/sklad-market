package org.example.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Request;
import feign.Response;
import org.example.config.internal.FileClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CustomerFileClientTest {
    @Test
    void sendsFileAsMultipartToExistingUploadEndpoint() {
        AtomicReference<Request> captured = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        FileClient client = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(HttpMessageConverters::new))
                .decoder((response, type) -> mapper.readValue(response.body().asInputStream(), mapper.constructType(type)))
                .client((request, options) -> {
                    captured.set(request);
                    return Response.builder().status(200).reason("OK").request(request)
                            .headers(Map.of("Content-Type", List.of("application/json")))
                            .body("{\"success\":true,\"data\":{\"id\":\"contract.pdf\",\"url\":\"/file\"}}", StandardCharsets.UTF_8)
                            .build();
                }).target(FileClient.class, "http://file-service");

        var result = client.upload(new MockMultipartFile("file", "contract.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8)), "UZ");

        Request request = captured.get();
        assertEquals("http://file-service/api/v1/attach/upload", request.url());
        assertEquals(Request.HttpMethod.POST, request.httpMethod());
        assertTrue(request.headers().get("Content-Type").iterator().next().startsWith("multipart/form-data;"));
        assertEquals(List.of("UZ"), List.copyOf(request.headers().get("Accept-Language")));
        String body = new String(request.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"file\""));
        assertTrue(body.contains("filename=\"contract.pdf\""));
        assertTrue(body.contains("%PDF-1.7"));
        assertEquals("contract.pdf", result.getData().getId());
    }
}
