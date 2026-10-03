package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ai.guest.*;
import org.example.ai.provider.ChatStream;
import org.example.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/ai/guest")
public class GuestAiController {
    private final GuestTrialStore store;
    private final GuestAnswerService answers;
    private final GuestClientAddress addresses;
    private final ThreadPoolTaskExecutor executor;
    public GuestAiController(GuestTrialStore store, GuestAnswerService answers, GuestClientAddress addresses,
            @Qualifier("aiGuestExecutor") ThreadPoolTaskExecutor executor) {
        this.store = store;
        this.answers = answers;
        this.addresses = addresses;
        this.executor = executor;
    }

    public record MessageRequest(@NotNull UUID requestId, @NotBlank @Size(max=2000) String content) {}

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestHeader(value="X-AI-Guest-Token", required=false) String token,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.successResponse(store.status(token));
    }

    @PostMapping("/session")
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.successResponse(store.create(addresses.resolve(request)));
    }

    @PostMapping("/messages")
    public DeferredResult<ApiResponse<Map<String, Object>>> send(
            @RequestHeader(value="X-AI-Guest-Token", required=false) String token,
            @RequestHeader(value="Accept-Language", required=false) String language,
            @Valid @RequestBody MessageRequest body, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        GuestTrialStore.Claim claim = store.claim(token, body.requestId(), body.content().trim(), addresses.resolve(request));
        DeferredResult<ApiResponse<Map<String, Object>>> deferred = new DeferredResult<>(180_000L);
        if (claim.cached() != null) {
            deferred.setResult(reply(token, claim.cached()));
            return deferred;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<ChatStream> active = new AtomicReference<>();
        Runnable cancel = () -> {
            cancelled.set(true);
            ChatStream stream = active.getAndSet(null);
            if (stream != null) stream.close();
        };
        deferred.onTimeout(() -> { cancel.run(); deferred.setErrorResult(new GuestTrialException("guest_unavailable", 503)); });
        deferred.onError(error -> cancel.run());
        deferred.onCompletion(cancel);
        try {
            executor.execute(() -> {
                try {
                    Map<String, Object> answer = answers.answer(claim, store.context(claim.tokenHash()), language, cancelled, active);
                    if (cancelled.get()) { store.fail(claim); return; }
                    store.complete(claim, answer);
                    deferred.setResult(reply(token, answer));
                } catch (RuntimeException error) {
                    try { store.fail(claim); }
                    finally { deferred.setErrorResult(new GuestTrialException("guest_unavailable", 503)); }
                }
            });
        } catch (RuntimeException rejected) {
            store.fail(claim);
            deferred.setErrorResult(new GuestTrialException("guest_unavailable", 503));
        }
        return deferred;
    }

    private ApiResponse<Map<String, Object>> reply(String token, Map<String, Object> answer) {
        Map<String, Object> result = new LinkedHashMap<>(store.status(token));
        result.put("answer", answer);
        return ApiResponse.successResponse(result);
    }
}
