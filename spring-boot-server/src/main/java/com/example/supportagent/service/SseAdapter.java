package com.example.supportagent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

import com.example.supportagent.util.Json;

/**
 * Converts Spring AI streaming {@link ChatResponse} chunks into the
 * OpenAI-Responses-style SSE events that the client understands (ported from the
 * original {@code lib/zhipu-stream.ts} adapter).
 */
public class SseAdapter {

    private long itemCounter = 0;

    private final List<String> events = new ArrayList<>();

    private String textItemId;
    private String fullText = "";
    private boolean textStarted = false;

    private final Map<String, ToolCallState> toolCalls = new LinkedHashMap<>();

    private record ToolCallState(int index, String id, String callId, String name, String arguments) {
    }

    /**
     * Process one streaming chunk and return the SSE frames it produced.
     */
    public List<String> onChunk(ChatResponse chunk) {
        events.clear();
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return List.of();
        }
        AssistantMessage message = chunk.getResult().getOutput();

        String text = message.getText();
        if (text != null && !text.isEmpty()) {
            emitTextDelta(text);
        }

        if (message.getToolCalls() != null) {
            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                emitToolCallFragment(toolCall);
            }
        }
        return List.copyOf(events);
    }

    /**
     * Finalize the stream and return the terminal SSE frames.
     */
    public List<String> onComplete() {
        events.clear();

        if (textStarted && !fullText.isEmpty()) {
            emit("response.output_text.done", outputTextDone());
            emit("response.output_item.done", outputTextItemDone());
        }

        for (ToolCallState tc : toolCalls.values()) {
            emit("response.function_call_arguments.done", functionCallArgumentsDone(tc));
            emit("response.output_item.done", functionCallItemDone(tc));
        }
        return List.copyOf(events);
    }

    private void emitTextDelta(String delta) {
        if (!textStarted) {
            textItemId = nextItemId("text");
            textStarted = true;
        }
        fullText += delta;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_id", textItemId);
        data.put("output_index", 0);
        data.put("content_index", 0);
        data.put("delta", delta);
        data.put("annotation", null);
        emit("response.output_text.delta", data);
    }

    private void emitToolCallFragment(AssistantMessage.ToolCall toolCall) {
        String key = (toolCall.id() != null && !toolCall.id().isBlank())
                ? toolCall.id()
                : "call_" + nextItemId("zhipu");
        ToolCallState existing = toolCalls.get(key);
        String name = toolCall.name() == null ? "" : toolCall.name();
        String args = toolCall.arguments() == null ? "" : toolCall.arguments();

        if (existing == null) {
            String itemId = nextItemId("fc");
            ToolCallState state = new ToolCallState(toolCalls.size(), itemId, key, name, args);
            toolCalls.put(key, state);
            emit("response.output_item.added", outputItemAdded(state));
            if (!args.isEmpty()) {
                emit("response.function_call_arguments.delta", functionCallArgumentsDelta(state, args));
            }
            return;
        }

        // Merge the latest name/arguments for this call id.
        ToolCallState merged = new ToolCallState(existing.index(), existing.id(), existing.callId(),
                name.isEmpty() ? existing.name() : name, args);
        toolCalls.put(key, merged);

        if (!args.isEmpty() && args.length() > existing.arguments().length()) {
            String newPart = args.substring(existing.arguments().length());
            emit("response.function_call_arguments.delta", functionCallArgumentsDelta(merged, newPart));
        }
    }

    private Map<String, Object> outputItemAdded(ToolCallState tc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call");
        item.put("id", tc.id());
        item.put("call_id", tc.callId());
        item.put("name", tc.name());
        item.put("arguments", "");
        item.put("status", "in_progress");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("output_index", tc.index() + 1);
        data.put("item", item);
        return data;
    }

    private Map<String, Object> functionCallArgumentsDelta(ToolCallState tc, String delta) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_id", tc.id());
        data.put("output_index", tc.index() + 1);
        data.put("delta", delta);
        return data;
    }

    private Map<String, Object> functionCallArgumentsDone(ToolCallState tc) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_id", tc.id());
        data.put("output_index", tc.index() + 1);
        data.put("call_id", tc.callId());
        data.put("name", tc.name());
        data.put("arguments", tc.arguments());
        return data;
    }

    private Map<String, Object> functionCallItemDone(ToolCallState tc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call");
        item.put("id", tc.id());
        item.put("call_id", tc.callId());
        item.put("name", tc.name());
        item.put("arguments", tc.arguments());
        item.put("status", "completed");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("output_index", tc.index() + 1);
        data.put("item", item);
        return data;
    }

    private Map<String, Object> outputTextDone() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_id", textItemId);
        data.put("output_index", 0);
        data.put("content_index", 0);
        data.put("text", fullText);
        return data;
    }

    private Map<String, Object> outputTextItemDone() {
        List<Map<String, Object>> content = List.of(Map.of(
                "type", "output_text",
                "text", fullText,
                "annotations", List.of()));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("id", textItemId);
        item.put("role", "assistant");
        item.put("status", "completed");
        item.put("content", content);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("output_index", 0);
        data.put("item", item);
        return data;
    }

    private void emit(String event, Map<String, Object> data) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("event", event);
        frame.put("data", data);
        events.add("data: " + Json.write(frame) + "\n\n");
    }

    private String nextItemId(String prefix) {
        return "item_" + prefix + "_" + System.currentTimeMillis() + "_" + itemCounter++;
    }
}
