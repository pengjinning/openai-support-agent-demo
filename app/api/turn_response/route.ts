import { MODEL } from "@/config/constants";
import { NextResponse } from "next/server";
import { getZhipuClient } from "@/lib/zhipu";
import {
  streamZhipuAsResponsesEvents,
  toChatMessages,
  toChatTools,
} from "@/lib/zhipu-stream";

export async function POST(request: Request) {
  try {
    const { messages, tools } = await request.json();
    console.log("Received messages:", messages);

    const client = getZhipuClient();

    // Create a ReadableStream that emits SSE data
    const stream = new ReadableStream({
      async start(controller) {
        const encoder = new TextEncoder();
        const emit = (event: { event: string; data: any }) => {
          controller.enqueue(
            encoder.encode(`data: ${JSON.stringify(event)}\n\n`)
          );
        };
        try {
          await streamZhipuAsResponsesEvents({
            client,
            model: MODEL,
            messages: toChatMessages(messages),
            tools: toChatTools(tools ?? []),
            emit,
          });
          // End of stream
          controller.close();
        } catch (error) {
          console.error("Error in streaming loop:", error);
          controller.close();
        }
      },
    });

    // Return the ReadableStream as SSE as the client expects
    return new Response(stream, {
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        Connection: "keep-alive",
      },
    });
  } catch (error) {
    console.error("Error in POST handler:", error);
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unknown error",
      },
      { status: 500 }
    );
  }
}
