package org.example.service;

import com.sun.net.httpserver.HttpServer;
import org.example.exp.OrderException;
import org.example.service.impl.ServiceTokenServiceImpl;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceTokenServiceTest {
    HttpServer server;
    Clock clock;
    AtomicReference<Instant> now;
    AtomicInteger requests;
    AtomicReference<String> body;
    AtomicInteger status;
    @BeforeEach void setup() throws Exception {
        now = new AtomicReference<>(Instant.parse("2026-11-02T10:00:00Z"));
        clock = mock(Clock.class); when(clock.instant()).thenAnswer(c -> now.get());
        requests = new AtomicInteger(); body = new AtomicReference<>(); status = new AtomicInteger(200);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            requests.incrementAndGet(); body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"service-token\",\"expires_in\":60}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
    }
    @AfterEach void cleanup() { server.stop(0); }
    @Test void clientCredentialsAreEncodedAndConcurrentCallsShareCachedToken() throws Exception {
        var service = service("a+b&c"); var pool = Executors.newFixedThreadPool(4);
        try {
            var calls = new ArrayList<Callable<String>>(); for(int i=0;i<12;i++) calls.add(service::token);
            for(var result : pool.invokeAll(calls)) assertThat(result.get()).isEqualTo("service-token");
        } finally { pool.shutdownNow(); }
        assertThat(requests.get()).isEqualTo(1);
        assertThat(body.get()).contains("grant_type=client_credentials", "client_id=order-service", "client_secret=a%2Bb%26c");
        now.set(now.get().plusSeconds(41)); assertThat(service.token()).isEqualTo("service-token");
        assertThat(requests.get()).isEqualTo(2);
    }
    @Test void missingCredentialsAndIssuerFailureFailClosed() {
        assertThatThrownBy(() -> service("").token()).isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
        assertThat(requests.get()).isZero(); status.set(401);
        assertThatThrownBy(() -> service("secret").token()).isInstanceOfSatisfying(OrderException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(503); assertThat(e.getMessage()).doesNotContain("secret");
        });
    }
    private ServiceTokenService service(String secret) {
        return new ServiceTokenServiceImpl("http://127.0.0.1:"+server.getAddress().getPort()+"/token", "order-service", secret, clock);
    }
}
