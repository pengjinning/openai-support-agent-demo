// Fake Zhipu upstream for local smoke testing (no real API needed).
// Implements SSE chat/completions with tool calling + embeddings.
import http from "http";

const PORT = 9901;

const server = http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    const url = req.url ?? "";
    if (url.endsWith("/chat/completions")) {
      const parsed = JSON.parse(body);
      const messages = parsed.messages ?? [];
      const hasToolResult = messages.some((m) => m.role === "tool");
      res.writeHead(200, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
      });
      const send = (obj) => res.write(`data: ${JSON.stringify(obj)}\n\n`);

      if (hasToolResult) {
        // Second turn: plain text answer
        send({ choices: [{ delta: { content: "Returns can be" } }] });
        send({ choices: [{ delta: { content: " requested within 30 days." } }] });
        send({ choices: [{ delta: {}, finish_reason: "stop" }] });
      } else {
        // First turn: emit some text then a streamed tool call
        send({ choices: [{ delta: { content: "Let me check that for you." } }] });
        send({
          choices: [
            {
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
          choices: [
            {
              delta: {
                tool_calls: [
                  { index: 0, function: { arguments: 'ery":"return policy"}' } },
                ],
              },
            },
          ],
        });
        send({ choices: [{ delta: {}, finish_reason: "tool_calls" }] });
      }
      res.end("data: [DONE]\n\n");
    } else if (url.endsWith("/embeddings")) {
      // Deterministic pseudo-embeddings: keyword overlap vector
      const parsed = JSON.parse(body);
      const inputs = Array.isArray(parsed.input) ? parsed.input : [parsed.input];
      const kw = ["return", "refund", "cancel", "order", "password", "delivery"];
      const data = inputs.map((text, i) => ({
        object: "embedding",
        index: i,
        embedding: kw.map((k) =>
          String(text).toLowerCase().includes(k) ? 1 : 0.05
        ),
      }));
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ object: "list", data, model: parsed.model }));
    } else {
      res.writeHead(404);
      res.end("{}");
    }
  });
});

server.listen(PORT, () => console.log(`fake zhipu on :${PORT}`));
