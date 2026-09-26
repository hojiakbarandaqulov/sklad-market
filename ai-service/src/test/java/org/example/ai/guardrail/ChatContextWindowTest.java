package org.example.ai.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatMessageInput;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatContextWindowTest {
    @Test void keepsOnlyThreeCompletePairsAndCurrentQuestion() {
        var messages = new ArrayList<ChatMessageInput>();
        messages.add(new ChatMessageInput("model", "orphan"));
        for (int i = 0; i < 20; i++) {
            messages.add(new ChatMessageInput("user", "q" + i));
            messages.add(new ChatMessageInput("model", "a" + i));
        }
        messages.add(new ChatMessageInput("user", "failed question"));
        messages.add(new ChatMessageInput("user", "current"));
        var bounded = ChatContextWindow.recentPairs(messages, 3);
        assertThat(bounded).hasSize(7);
        assertThat(bounded.get(0).text()).isEqualTo("q17");
        assertThat(bounded.get(6).text()).isEqualTo("current");
    }

    @Test void enforcesWholeRequestSizeWithoutDroppingCurrentQuestion() {
        var window = new ChatContextWindow(new ObjectMapper(), 8192);
        var history = List.of(new ChatMessageInput("user", "x".repeat(8000)),
                new ChatMessageInput("model", "y".repeat(8000)), new ChatMessageInput("user", "current"));
        var request = new ChatGenerationRequest("test", "instructions", history, List.of(), List.of(), 0, 10);
        assertThat(window.fit(request).history()).containsExactly(history.get(2));
        var oversized = new ChatGenerationRequest("test", "x".repeat(10000), history, List.of(), List.of(), 0, 10);
        assertThatThrownBy(() -> window.fit(oversized)).hasMessageContaining("too much detail");
    }
}
