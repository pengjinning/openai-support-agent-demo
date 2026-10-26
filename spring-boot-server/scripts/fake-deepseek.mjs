// Fake DeepSeek upstream for local smoke testing (no real API key needed).
// Implements the OpenAI-compatible streaming /chat/completions endpoint used by
// Spring AI's DeepSeek client, including a streamed tool call.
//
// Usage:
//   node scripts/fake-deepseek.mjs
//   DEEPSEEK_BASE_URL=http://localhost:9899 DEEPSEEK_API_KEY=test mvn spring-boot:run

import http from "http";

const PORT = 9899;

const server = http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    const url = (req.url || "").split("?")[0];
    if (!url.endsWith("/chat/completions")) {
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "not found" }));
      return;
    }

    const parsed = JSON.parse(body || "{}");
    const messages = parsed.messages ?? [];
    const hasToolResult = messages.some((m) => m.role === "tool");

    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });
    const send = (obj) => res.write(`data: ${JSON.stringify(obj)}\n\n`);

    if (hasToolResult) {
      // Second turn: plain text answer, no tool calls.
      send({ id: "chatcmpl-fake-2", choices: [{ index: 0, delta: { role: "assistant", content: "Returns can be" } }] });
      send({ id: "chatcmpl-fake-2", choices: [{ index: 0, delta: { content: " requested within 30 days." } }] });
      send({ id: "chatcmpl-fake-2", choices: [{ index: 0, delta: {}, finish_reason: "stop" }] });
    } else {
      // First turn: some text, then a streamed search_knowledge_base call.
      send({ id: "chatcmpl-fake-1", choices: [{ index: 0, delta: { role: "assistant", content: "Let me check that for you." } }] });
      send({
        id: "chatcmpl-fake-1",
        choices: [
          {
            index: 0,
            delta: {
              tool_calls: [
                {
                  index: 0,
                  id: "call_fake_1",
                  type: "function",
                  function: { name: "search_knowledge_base", arguments: '{"qu' },
                },
              ],
            },
          },
        ],
      });
      send({
        id: "chatcmpl-fake-1",
        choices: [
          {
            index: 0,
            delta: {
              tool_calls: [{ index: 0, function: { arguments: 'ery":"return policy"}' } }],
            },
          },
        ],
      });
      send({ id: "chatcmpl-fake-1", choices: [{ index: 0, delta: {}, finish_reason: "tool_calls" }] });
    }
    res.end("data: [DONE]\n\n");
  });
});

server.listen(PORT, () => console.log(`fake deepseek on :${PORT}`));
