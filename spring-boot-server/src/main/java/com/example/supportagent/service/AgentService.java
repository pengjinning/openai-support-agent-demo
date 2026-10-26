package com.example.supportagent.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.example.supportagent.config.AppConfig;
import com.example.supportagent.tools.SupportTools;
import com.example.supportagent.util.Json;

import reactor.core.publisher.Flux;

/**
 * Core AI orchestration: converts the client's Responses-style conversation
 * items into Spring AI messages, streams a DeepSeek chat completion and re-emits
 * the result as Responses-style SSE events (ported from
 * {@code lib/assistant.ts} + {@code lib/zhipu-stream.ts}).
 */
@Service
public class AgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final AppConfig appConfig;
    private final SupportTools supportTools;

    public AgentService(ChatClient.Builder chatClientBuilder, AppConfig appConfig, SupportTools supportTools) {
        this.chatClientBuilder = chatClientBuilder;
        this.appConfig = appConfig;
        this.supportTools = supportTools;
    }

    /**
     * Streams a single model turn to the given output stream as raw SSE bytes.
     * This method blocks until the model stream completes.
     */
    public void streamTurn(List<Map<String, Object>> messages, OutputStream out) throws IOException {
        SseAdapter adapter = new SseAdapter();

        // Always prepend the developer (system) prompt, regardless of client history.
        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage(appConfig.developerPrompt()));
        history.addAll(toSpringAiMessages(messages));

        ChatClient client = chatClientBuilder.build();
        Flux<org.springframework.ai.chat.model.ChatResponse> flux = client.prompt()
                // The client executes tools via REST to preserve the
                // human-in-the-loop flow, so disable Spring AI's auto tool calling.
                .advisors(spec -> spec.param(
                        org.springframework.ai.chat.client.ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(),
                        false))
                .messages(history)
                .tools(supportTools)
                .stream()
                .chatResponse();

        CountDownLatch latch = new CountDownLatch(1);
        flux.subscribe(
                chunk -> writeAll(out, adapter.onChunk(chunk)),
                error -> {
                    writeAll(out, adapter.onComplete());
                    writeAll(out, errorFrame(error));
                    latch.countDown();
                },
                () -> {
                    writeAll(out, adapter.onComplete());
                    latch.countDown();
                });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        out.flush();
    }

    /**
     * Converts client conversation items into Spring AI messages, mirroring the
     * original {@code toChatMessages()} conversion.
     */
    public List<Message> toSpringAiMessages(List<Map<String, Object>> items) {
        List<Message> messages = new ArrayList<>();
        if (items == null) {
            return messages;
        }
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String type = asString(item.get("type"));
            String role = asString(item.get("role"));

            if ("function_call".equals(type)) {
                String callId = firstNonBlank(asString(item.get("call_id")), asString(item.get("id")));
                String name = asString(item.get("name"));
                String arguments = item.get("arguments") == null ? "{}" : String.valueOf(item.get("arguments"));
                AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                        callId == null ? "" : callId, "function", name == null ? "" : name, arguments);
                messages.add(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall))
                        .build());
            } else if ("function_call_output".equals(type)) {
                String callId = asString(item.get("call_id"));
                String output = item.get("output") == null ? "" : String.valueOf(item.get("output"));
                ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(
                        callId == null ? "" : callId, "", output);
                messages.add(ToolResponseMessage.builder().responses(List.of(response)).build());
            } else if (role != null) {
                String content = extractText(item.get("content"));
                switch (role) {
                    case "developer", "system" -> messages.add(new SystemMessage(content == null ? "" : content));
                    case "user" -> messages.add(new UserMessage(content == null ? "" : content));
                    case "assistant" -> addAssistantText(messages, content);
                    default -> messages.add(new UserMessage(content == null ? "" : content));
                }
            }
        }
        return messages;
    }

    private void addAssistantText(List<Message> messages, String content) {
        String text = content == null ? "" : content;
        Message previous = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (previous instanceof AssistantMessage assistant && !assistant.hasToolCalls()) {
            String merged = (assistant.getText() == null ? "" : assistant.getText()) + text;
            messages.set(messages.size() - 1, new AssistantMessage(merged));
        } else {
            messages.add(new AssistantMessage(text));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object c : list) {
                if (c instanceof String s) {
                    sb.append(s);
                } else if (c instanceof Map<?, ?> m) {
                    Object text = m.get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    private void writeAll(OutputStream out, List<String> frames) {
        for (String frame : frames) {
            try {
                out.write(frame.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // Client disconnected; nothing else we can do.
                return;
            }
        }
    }

    private List<String> errorFrame(Throwable error) {
        Map<String, Object> data = Map.of("message",
                error.getMessage() == null ? "Unknown error" : error.getMessage());
        Map<String, Object> frame = Map.of("event", "error", "data", data);
        return List.of("data: " + Json.write(frame) + "\n\n");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
