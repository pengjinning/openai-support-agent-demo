// Customer Support Agent — chat orchestration (ported from lib/assistant.ts,
// stores and the React views of the original NextJS app).

(() => {
  "use strict";

  // ---------- State ----------
  const state = {
    agentName: "Blossom",
    customerDetails: {},
    // Items rendered in the chat (messages + tool calls).
    chatMessages: [],
    // Items sent to the backend as conversation history.
    conversationItems: [],
    recommendedActions: [],
    relevantArticles: [],
    relevantArticlesLoading: false,
    // Streaming suggested reply for the agent.
    suggestedText: "",
    suggestedDone: false,
    agentTyping: false,
    userTyping: false,
  };

  const AGENT_TOOLS = new Set([
    "cancel_order",
    "reset_password",
    "send_replacement",
    "create_refund",
    "issue_voucher",
    "create_return",
    "create_complaint",
    "update_info",
  ]);

  // ---------- DOM helpers ----------
  const $ = (id) => document.getElementById(id);
  const customerChat = $("customerChat");
  const agentChat = $("agentChat");
  const customerInput = $("customerInput");
  const agentInput = $("agentInput");
  const customerSend = $("customerSend");
  const agentSend = $("agentSend");
  const customerComposer = $("customerComposer");
  const agentComposer = $("agentComposer");
  const suggestedArea = $("suggestedArea");
  const suggestedBubble = $("suggestedBubble");
  const suggestedActions = $("suggestedActions");

  function escapeHtml(text) {
    return String(text)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  // Minimal markdown renderer: escape first, then apply a few safe transforms.
  function renderMarkdown(text) {
    if (text == null) return "";
    let html = escapeHtml(text);
    const lines = html.split("\n");
    let inList = false;
    const out = [];
    for (let line of lines) {
      // Inline emphasis/bold/code
      line = line
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/`([^`]+)`/g, "<code>$1</code>");
      if (/^\s*[-*]\s+/.test(line)) {
        if (!inList) {
          out.push("<ul>");
          inList = true;
        }
        out.push("<li>" + line.replace(/^\s*[-*]\s+/, "") + "</li>");
        continue;
      }
      if (inList) {
        out.push("</ul>");
        inList = false;
      }
      if (/^#{1,6}\s+/.test(line)) {
        const level = line.match(/^#+/)[0].length;
        out.push(
          `<h${level}>${line.replace(/^#+\s+/, "")}</h${level}>`
        );
      } else if (line.trim() === "") {
        out.push("");
      } else {
        out.push(`<p>${line}</p>`);
      }
    }
    if (inList) out.push("</ul>");
    return out.join("");
  }

  function bubbleHtml(role, text, extraClass) {
    const cls = "bubble " + (role === "user" ? "user" : "agent") + (extraClass ? " " + extraClass : "");
    return `<div class="msg-row ${role === "user" ? "right" : "left"}"><div class="${cls}">${renderMarkdown(text)}</div></div>`;
  }

  function typingHtml() {
    return `<div class="msg-row left"><div class="bubble agent typing"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div></div>`;
  }

  function toolCallHtml(item, idx) {
    const label = item.name && AGENT_TOOLS.has(item.name)
      ? `Suggested ${formatName(item.name)}`
      : item.status === "completed"
        ? `Called ${formatName(item.name || "")}`
        : `Calling ${formatName(item.name || "")}...`;
    const argsJson = item.parsedArguments ? JSON.stringify(item.parsedArguments, null, 2) : "";
    let outputJson = "";
    if (item.output) {
      try {
        outputJson = JSON.stringify(JSON.parse(item.output), null, 2);
      } catch {
        outputJson = item.output;
      }
    }
    return `
      <div class="msg-row right" style="flex-direction:column;align-items:flex-end;gap:2px;">
        <div class="tool-call" id="tool-call-${idx}" data-idx="${idx}">
          <span>⚡</span><span>${label}</span>
          <span class="chevron">›</span>
        </div>
        <div class="tool-detail" id="tool-detail-${idx}">
          <pre>${escapeHtml(argsJson || "{}")}</pre>
          <hr style="border:none;border-top:1px solid #eee;margin:8px 0;" />
          <pre>${escapeHtml(outputJson || "Waiting for result...")}</pre>
        </div>
      </div>`;
  }

  function formatName(name) {
    return String(name || "").replace(/_/g, " ").replace(/-/g, " ");
  }

  function renderChat(container, view, includeToolCalls) {
    let html = "";
    for (let i = 0; i < state.chatMessages.length; i++) {
      const item = state.chatMessages[i];
      if (item.type === "message") {
        const text = item.content && item.content[0] ? item.content[0].text : "";
        const own = view === "user" ? item.role === "user" : item.role === "agent";
        // Right-align own messages, left-align the other party's.
        const role = own ? (view === "user" ? "user" : "agent") : (view === "user" ? "agent" : "user");
        html += bubbleHtml(role, text);
      } else if (item.type === "tool_call" && includeToolCalls) {
        html += toolCallHtml(item, i);
      }
    }

    if (view === "user" && state.agentTyping) {
      html += typingHtml();
    } else if (view === "agent" && state.userTyping) {
      html += typingHtml();
    }

    container.innerHTML = html;

    // Wire up tool call toggles.
    container.querySelectorAll(".tool-call").forEach((el) => {
      el.addEventListener("click", () => {
        const idx = el.getAttribute("data-idx");
        const detail = $("tool-detail-" + idx);
        const wasOpen = el.classList.contains("open");
        el.classList.toggle("open", !wasOpen);
        detail.classList.toggle("open", !wasOpen);
      });
    });
  }

  function renderCustomerDetails() {
    const d = state.customerDetails || {};
    const items = [
      ["Name", d.name],
      ["ID", d.id],
      ["# Orders", d.orderNb],
      ["Signup Date", d.signupDate],
    ];
    $("customerDetails").innerHTML = items
      .map(([label, value]) => `<div class="detail-item"><span class="label">${escapeHtml(label)}</span><span>${escapeHtml(value ?? "")}</span></div>`)
      .join("");
  }

  function renderRecommendedActions() {
    const actions = state.recommendedActions || [];
    $("recommendedActions").innerHTML = actions
      .map((action, i) => `<button type="button" class="action-chip" data-action="${i}">${escapeHtml(formatName(action.name))}</button>`)
      .join("");
    $("recommendedActions").querySelectorAll(".action-chip").forEach((el) => {
      el.addEventListener("click", () => openActionDialog(parseInt(el.getAttribute("data-action"), 10)));
    });
  }

  function articleLink(article) {
    const section = article.type === "faq" ? "faq" : "kb";
    return `/${section}?section=${encodeURIComponent(article.filename || "")}`;
  }

  function renderRelevantArticles() {
    const container = $("relevantArticles");
    if (state.relevantArticlesLoading) {
      container.innerHTML = `<div class="article-skeleton"></div><div class="article-skeleton"></div>`;
      return;
    }
    const articles = state.relevantArticles || [];
    container.innerHTML = articles
      .map((a) => {
        const typeLabel = a.type === "faq" ? "PUBLIC FAQ" : "INTERNAL";
        const href = a.link || articleLink(a);
        return `<div class="article">
          <div class="article-head">
            <span class="article-title">${escapeHtml(a.title || "")}</span>
            <a class="article-type" href="${escapeHtml(href)}" target="_blank">${typeLabel} ↗</a>
          </div>
          <div class="article-content">${escapeHtml(a.content || "")}</div>
        </div>`;
      })
      .join("");
  }

  function renderSuggested() {
    if (!state.suggestedText && !state.suggestedDone) {
      suggestedArea.classList.add("hidden");
      return;
    }
    suggestedArea.classList.remove("hidden");
    suggestedBubble.innerHTML = `<div class="bubble agent suggestion">${renderMarkdown(state.suggestedText)}</div>`;
    if (state.suggestedDone) {
      suggestedActions.classList.remove("hidden");
    } else {
      suggestedActions.classList.add("hidden");
    }
  }

  function renderAll() {
    renderChat(customerChat, "user", false);
    renderChat(agentChat, "agent", true);
    renderCustomerDetails();
    renderRecommendedActions();
    renderRelevantArticles();
    renderSuggested();
  }

  // ---------- Tool execution ----------
  async function api(path, options) {
    const res = await fetch(path, options);
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`Request failed: ${res.status} ${text}`);
    }
    return res.json();
  }

  function argsOf(params) {
    return params || {};
  }

  async function executeTool(name, params) {
    const a = argsOf(params);
    switch (name) {
      case "search_knowledge_base":
        return api("/api/kb_search", jsonPost({ query: a.query || "" }));
      case "get_order":
        return api(`/api/orders/${encodeURIComponent(a.orderId || "")}`);
      case "get_order_history":
        return api(`/api/users/${encodeURIComponent(a.userId || "")}/order_history`);
      case "cancel_order":
        return api(`/api/orders/${encodeURIComponent(a.orderId || "")}/cancel`, jsonPost({}));
      case "create_refund":
        return api(`/api/orders/${encodeURIComponent(a.orderId || "")}/create_refund`, jsonPost({ amount: a.amount, reason: a.reason }));
      case "create_return":
        return api(`/api/orders/${encodeURIComponent(a.orderId || "")}/create_return`, jsonPost({ product_ids: a.productIds || [] }));
      case "send_replacement":
        return api(`/api/orders/${encodeURIComponent(a.orderId || "")}/send_replacement`, jsonPost({ product_id: a.productId }));
      case "reset_password":
        return api(`/api/users/${encodeURIComponent(a.userId || "")}/reset_password`, jsonPost({}));
      case "update_info":
        return api(`/api/users/${encodeURIComponent(a.userId || "")}/update_info`, jsonPost({ info: a.info || {} }));
      case "create_complaint":
        return api("/api/complaints/create", jsonPost({ user_id: a.userId, type: a.type, details: a.details, order_id: a.orderId }));
      case "create_ticket":
        return api("/api/tickets/create", jsonPost({ user_id: a.userId, type: a.type, details: a.details, order_id: a.orderId }));
      case "issue_voucher":
        return api("/api/vouchers/create", jsonPost({ user_id: a.userId, amount: a.amount, reason: a.reason }));
      default:
        throw new Error("Unknown tool: " + name);
    }
  }

  function jsonPost(body) {
    return {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    };
  }

  // ---------- AI turn (SSE) ----------
  // True while a user-initiated turn (send or confirmed action) is in flight.
  let userTurnActive = false;

  async function processTurn() {
    state.suggestedText = "";
    state.suggestedDone = false;
    state.agentTyping = true;
    renderAll();

    try {
      const response = await fetch("/api/turn_response", jsonPost({ messages: state.conversationItems }));
      if (!response.ok || !response.body) {
        throw new Error(`turn_response failed: ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n\n");
        buffer = lines.pop() || "";
        for (const line of lines) {
          if (!line.startsWith("data: ")) continue;
          const dataStr = line.slice(6);
          if (dataStr === "[DONE]") continue;
          let frame;
          try {
            frame = JSON.parse(dataStr);
          } catch {
            continue;
          }
          await handleSseEvent(frame.event, frame.data, {});
        }
      }
      if (buffer.startsWith("data: ")) {
        const dataStr = buffer.slice(6);
        if (dataStr !== "[DONE]") {
          try {
            const frame = JSON.parse(dataStr);
            await handleSseEvent(frame.event, frame.data, {});
          } catch { /* ignore */ }
        }
      }
    } catch (error) {
      console.error("Error processing turn:", error);
    } finally {
      state.agentTyping = false;
      renderAll();
    }
  }

  async function handleSseEvent(event, data, ctx) {
    switch (event) {
      case "response.output_text.delta": {
        state.agentTyping = true;
        state.suggestedText += typeof data.delta === "string" ? data.delta : "";
        renderAll();
        break;
      }
      case "response.output_text.done": {
        state.suggestedDone = true;
        renderAll();
        break;
      }
      case "response.output_item.added": {
        const item = data && data.item;
        if (!item || item.type !== "function_call") break;
        state.chatMessages.push({
          type: "tool_call",
          tool_type: "function_call",
          status: "in_progress",
          id: item.id,
          name: item.name,
          call_id: item.call_id,
          arguments: item.arguments || "",
          parsedArguments: {},
          output: null,
        });
        if (item.name === "search_knowledge_base") {
          state.relevantArticlesLoading = true;
        }
        renderAll();
        break;
      }
      case "response.function_call_arguments.delta": {
        ctx.functionArgs = (ctx.functionArgs || "") + (data.delta || "");
        const msg = findToolCall(data.item_id);
        if (msg) {
          msg.arguments = ctx.functionArgs;
          msg.parsedArguments = safeParseJson(ctx.functionArgs);
          renderAll();
        }
        break;
      }
      case "response.function_call_arguments.done": {
        const finalArgs = data.arguments || "";
        ctx.functionArgs = finalArgs;
        const msg = findToolCall(data.item_id);
        if (msg) {
          msg.arguments = finalArgs;
          msg.parsedArguments = safeParseJson(finalArgs);
          msg.status = "completed";
          if (msg.name === "search_knowledge_base") {
            state.relevantArticlesLoading = true;
          }
          if (msg.name && AGENT_TOOLS.has(msg.name)) {
            upsertRecommendedAction(msg.name, msg.parsedArguments);
          }
          renderAll();
        }
        break;
      }
      case "response.output_item.done": {
        const item = data && data.item;
        if (!item || item.type !== "function_call") break;

        // Record the model's tool call in the conversation.
        state.conversationItems.push({
          type: "function_call",
          id: item.id,
          call_id: item.call_id,
          name: item.name,
          arguments: item.arguments || "{}",
        });

        const msg = findToolCall(item.id);
        if (msg) {
          try {
            const toolResult = await executeTool(item.name, msg.parsedArguments || safeParseJson(item.arguments || "{}"));
            msg.output = JSON.stringify(toolResult);
            msg.call_id = item.call_id;
            if (item.name === "search_knowledge_base") {
              setRelevantArticles(toolResult && toolResult.results);
              state.relevantArticlesLoading = false;
            }
          } catch (error) {
            msg.output = JSON.stringify({ error: String(error && error.message ? error.message : error) });
            if (item.name === "search_knowledge_base") {
              state.relevantArticlesLoading = false;
            }
          }
          renderAll();

          state.conversationItems.push({
            type: "function_call_output",
            call_id: msg.call_id || item.call_id,
            output: msg.output,
          });
        }

        // Continue the model loop after tool output.
        await processTurn();
        break;
      }
      default:
        break;
    }
  }

  function safeParseJson(text) {
    try {
      return JSON.parse(text || "{}");
    } catch {
      return {};
    }
  }

  function findToolCall(itemId) {
    return state.chatMessages.find(
      (m) => m.type === "tool_call" && (m.id === itemId || m.call_id === itemId)
    );
  }

  function upsertRecommendedAction(name, parameters) {
    state.recommendedActions = state.recommendedActions.filter((a) => a.name !== name);
    state.recommendedActions.push({ name, parameters });
  }

  function setRelevantArticles(results) {
    const articles = (results || [])
      .map((result) => {
        const lines = (result.text || "").split("\n");
        const title = (lines[0] || "").replace(/#/g, "").trim();
        const content = lines.slice(1).join("\n");
        const attributes = result.attributes || {};
        return {
          title,
          content,
          type: attributes.type || "knowledge_base",
          filename: attributes.filename || "",
          link: "",
          score: result.score,
        };
      })
      .filter((a) => a.score > 0.5)
      .sort((a, b) => b.score - a.score);
    state.relevantArticles = articles;
  }

  // ---------- Message sending ----------
  function addChatMessage(role, text) {
    state.chatMessages.push({
      type: "message",
      role,
      content: [{ type: role === "user" ? "input_text" : "output_text", text }],
    });
  }

  async function sendCustomerMessage(text) {
    if (!text.trim() || userTurnActive) return;
    userTurnActive = true;
    addChatMessage("user", text.trim());
    state.conversationItems.push({ role: "user", content: text.trim() });
    renderAll();
    try {
      await processTurn();
    } finally {
      userTurnActive = false;
    }
  }

  function sendAgentMessage(text) {
    if (!text.trim()) return;
    addChatMessage("agent", text.trim());
    state.conversationItems.push({ role: "assistant", content: text.trim() });
    state.suggestedText = "";
    state.suggestedDone = false;
    renderAll();
  }

  // ---------- Recommended action dialog ----------
  let activeActionIndex = null;

  function openActionDialog(index) {
    const action = state.recommendedActions[index];
    if (!action) return;
    activeActionIndex = index;
    $("actionTitle").textContent = formatName(action.name);
    $("actionFnName").textContent = action.name;
    const params = action.parameters || {};
    $("actionParams").innerHTML = Object.entries(params)
      .map(
        ([key, value]) => `<div class="dialog-param">
          <label for="param-${escapeHtml(key)}">${escapeHtml(key)}</label>
          <input id="param-${escapeHtml(key)}" value="${escapeHtml(typeof value === "object" ? JSON.stringify(value) : value)}" />
        </div>`
      )
      .join("");
    $("actionDialog").showModal();
  }

  function closeActionDialog() {
    $("actionDialog").close();
    activeActionIndex = null;
  }

  async function confirmAction() {
    if (userTurnActive) return;
    const action = state.recommendedActions[activeActionIndex];
    if (!action) return;
    const params = {};
    $("actionParams").querySelectorAll("input").forEach((input) => {
      const key = input.id.replace(/^param-/, "");
      let value = input.value;
      try {
        value = JSON.parse(value);
      } catch { /* keep as string */ }
      params[key] = value;
    });

    closeActionDialog();
    userTurnActive = true;

    try {
      const result = await executeTool(action.name, params);
      state.recommendedActions = state.recommendedActions.filter((a) => a.name !== action.name);
      state.conversationItems.push({
        role: "assistant",
        content: JSON.stringify(result),
      });
      renderAll();
      await processTurn();
    } catch (error) {
      console.error("Action failed:", error);
      renderAll();
    } finally {
      userTurnActive = false;
    }
  }

  // ---------- Wire up UI ----------
  function bindEvents() {
    customerComposer.addEventListener("submit", (e) => {
      e.preventDefault();
      sendCustomerMessage(customerInput.value);
      customerInput.value = "";
      updateSendButtons();
    });
    agentComposer.addEventListener("submit", (e) => {
      e.preventDefault();
      sendAgentMessage(agentInput.value);
      agentInput.value = "";
      updateSendButtons();
    });

    customerInput.addEventListener("input", () => {
      updateSendButtons();
      state.userTyping = customerInput.value.length > 0;
      renderAll();
    });
    agentInput.addEventListener("input", () => {
      updateSendButtons();
    });

    customerInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        customerComposer.requestSubmit();
      }
    });
    agentInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        agentComposer.requestSubmit();
      }
    });

    $("sendNowBtn").addEventListener("click", () => {
      sendAgentMessage(state.suggestedText);
    });
    $("editBtn").addEventListener("click", () => {
      agentInput.value = state.suggestedText;
      state.suggestedText = "";
      state.suggestedDone = false;
      updateSendButtons();
      renderAll();
    });

    $("actionCancel").addEventListener("click", closeActionDialog);
    $("actionConfirm").addEventListener("click", confirmAction);

    $("customerToggle").addEventListener("click", () => setMobileView("customer"));
    $("agentToggle").addEventListener("click", () => setMobileView("agent"));
  }

  function setMobileView(view) {
    document.body.classList.remove("mobile-customer", "mobile-agent");
    document.body.classList.add(view === "customer" ? "mobile-customer" : "mobile-agent");
    $("customerToggle").classList.toggle("active", view === "customer");
    $("agentToggle").classList.toggle("active", view === "agent");
  }

  function updateSendButtons() {
    customerSend.disabled = !customerInput.value.trim();
    agentSend.disabled = !agentInput.value.trim();
  }

  async function boot() {
    bindEvents();
    try {
      const meta = await api("/api/meta");
      state.agentName = meta.agentName || "Blossom";
      state.customerDetails = meta.customerDetails || {};
      if (meta.initialMessage) {
        addChatMessage("agent", meta.initialMessage);
      }
      if (Array.isArray(meta.defaultArticles)) {
        state.relevantArticles = meta.defaultArticles;
      }
      if (meta.defaultAction) {
        state.recommendedActions = [meta.defaultAction];
      }
    } catch (error) {
      console.error("Failed to load meta:", error);
      state.customerDetails = {};
      addChatMessage("agent", "Hi, I'm Blossom, your support representative. How can I help you today?");
    }
    updateSendButtons();
    renderAll();
  }

  boot();
})();
