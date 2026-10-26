import OpenAI from "openai";
import {
  ChatCompletionMessageParam,
  ChatCompletionTool,
} from "openai/resources/chat/completions";

/**
 * Adapter that calls the Zhipu AI (智谱) OpenAI-compatible Chat Completions API
 * and re-emits the stream as OpenAI Responses API SSE events, so the existing
 * client-side event handling in lib/assistant.ts keeps working unchanged.
 *
 * Responses event shapes are documented here:
 * https://platform.openai.com/docs/api-reference/responses-streaming
 */

export interface ResponsesEventEmitter {
  (event: { event: string; data: any }): void;
}

let itemCounter = 0;
const nextItemId = (prefix: string) =>
  `item_${prefix}_${Date.now()}_${itemCounter++}`;

/**
 * Converts conversation items stored client-side into Chat Completions messages.
 * - developer prompt -> system message
 * - Responses function_call / function_call_output items -> assistant/tool messages
 */
export function toChatMessages(items: any[]): ChatCompletionMessageParam[] {
  const messages: ChatCompletionMessageParam[] = [];
  for (const item of items) {
    if (typeof item === "string") {
      messages.push({ role: "user", content: item });
      continue;
    }
    if (!item || typeof item !== "object") continue;

    const content =
      typeof item.content === "string"
        ? item.content
        : Array.isArray(item.content)
        ? item.content
            .map((c: any) => (typeof c === "string" ? c : (c.text ?? "")))
            .join("")
        : "";

    if (item.type === "function_call") {
      messages.push({
        role: "assistant",
        content: null,
        tool_calls: [
          {
            id: item.call_id ?? item.id,
            type: "function" as const,
            function: {
              name: item.name,
              arguments: item.arguments ?? "{}",
            },
          },
        ],
      });
    } else if (item.type === "function_call_output") {
      messages.push({
        role: "tool",
        tool_call_id: item.call_id,
        content: item.output ?? "",
      });
    } else if (item.role) {
      const role = item.role === "developer" ? "system" : item.role;
      const message = {
        role,
        content: content || null,
      } as ChatCompletionMessageParam;
      // Merge consecutive assistant messages (text turn + tool call turn)
      const previous = messages[messages.length - 1];
      if (
        role === "assistant" &&
        previous?.role === "assistant" &&
        !previous.tool_calls
      ) {
        messages[messages.length - 1] = {
          ...previous,
          content: `${previous.content ?? ""}${content}`,
        } as ChatCompletionMessageParam;
      } else {
        messages.push(message);
      }
    }
  }
  return messages;
}

/**
 * Converts Responses-style flat tool definitions (name/parameters, built in
 * lib/tools/tools.ts) into Chat Completions `tools` format.
 */
export function toChatTools(tools: any[]): ChatCompletionTool[] {
  return tools
    .filter((tool) => tool?.type === "function" && tool.name)
    .map((tool) => ({
      type: "function" as const,
      function: {
        name: tool.name,
        description: tool.description ?? "",
        parameters: tool.parameters,
      },
    }));
}

interface InFlightToolCall {
  index: number;
  id: string;
  name: string;
  arguments: string;
  itemId: string;
}

/**
 * Streams a Zhipu chat completion and re-emits Responses API events.
 */
export async function streamZhipuAsResponsesEvents(params: {
  client: OpenAI;
  model: string;
  messages: ChatCompletionMessageParam[];
  tools?: ChatCompletionTool[];
  emit: ResponsesEventEmitter;
}) {
  const { client, model, messages, tools, emit } = params;

  const stream = await client.chat.completions.create({
    model,
    messages,
    tools: tools && tools.length > 0 ? tools : undefined,
    stream: true,
  });

  const textItemId = nextItemId("text");
  let fullText = "";

  // Track in-flight function calls, grouped by their tool_calls index
  const toolCalls: InFlightToolCall[] = [];

  for await (const chunk of stream) {
    const choice = chunk.choices?.[0];
    if (!choice) continue;
    const delta = choice.delta ?? {};

    // Visible text content (reasoning_content deltas are intentionally ignored)
    if (delta.content) {
      fullText += delta.content;
      emit({
        event: "response.output_text.delta",
        data: {
          type: "response.output_text.delta",
          item_id: textItemId,
          output_index: 0,
          content_index: 0,
          delta: delta.content,
          annotation: null,
          logprobs: [],
          obfuscation: "",
        },
      });
    }

    // Tool call fragments, grouped by index
    if (delta.tool_calls) {
      for (const fragment of delta.tool_calls) {
        const fragmentIndex = fragment.index ?? 0;
        let tc = toolCalls.find((t) => t.index === fragmentIndex);
        if (!tc) {
          tc = {
            index: fragmentIndex,
            id: fragment.id ?? `call_${nextItemId("zhipu")}`,
            name: fragment.function?.name ?? "",
            arguments: "",
            itemId: nextItemId("fc"),
          };
          toolCalls.push(tc);
          emit({
            event: "response.output_item.added",
            data: {
              type: "response.output_item.added",
              output_index: tc.index + 1,
              item: {
                type: "function_call",
                id: tc.itemId,
                call_id: tc.id,
                name: tc.name,
                arguments: "",
                status: "in_progress",
              },
            },
          });
        }
        if (fragment.function?.name) {
          tc.name = fragment.function.name;
        }
        if (fragment.function?.arguments) {
          tc.arguments += fragment.function.arguments;
          emit({
            event: "response.function_call_arguments.delta",
            data: {
              type: "response.function_call_arguments.delta",
              item_id: tc.itemId,
              output_index: tc.index + 1,
              delta: fragment.function.arguments,
            },
          });
        }
      }
    }
  }

  // Close out the visible text item, if any
  if (fullText) {
    emit({
      event: "response.output_text.done",
      data: {
        type: "response.output_text.done",
        item_id: textItemId,
        output_index: 0,
        content_index: 0,
        text: fullText,
      },
    });
    emit({
      event: "response.output_item.done",
      data: {
        type: "response.output_item.done",
        output_index: 0,
        item: {
          type: "message",
          id: textItemId,
          role: "assistant",
          status: "completed",
          content: [{ type: "output_text", text: fullText, annotations: [] }],
        },
      },
    });
  }

  // Close out function call items. The client executes tools and starts a new
  // turn when it receives these events.
  for (const tc of toolCalls) {
    emit({
      event: "response.function_call_arguments.done",
      data: {
        type: "response.function_call_arguments.done",
        item_id: tc.itemId,
        output_index: tc.index + 1,
        call_id: tc.id,
        name: tc.name,
        arguments: tc.arguments,
      },
    });
    emit({
      event: "response.output_item.done",
      data: {
        type: "response.output_item.done",
        output_index: tc.index + 1,
        item: {
          type: "function_call",
          id: tc.itemId,
          call_id: tc.id,
          name: tc.name,
          arguments: tc.arguments,
          status: "completed",
        },
      },
    });
  }
}
