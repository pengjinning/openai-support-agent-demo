// Article list pages (/kb and /faq) — ported from components/ListArticles.tsx.
(() => {
  "use strict";

  const folder = window.__ARTICLE_FOLDER__ || "knowledge_base";

  function escapeHtml(text) {
    return String(text)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function renderMarkdown(text) {
    let html = escapeHtml(text || "");
    const lines = html.split("\n");
    let inList = false;
    const out = [];
    for (let line of lines) {
      line = line
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/`([^`]+)`/g, "<code>$1</code>");
      if (/^\s*[-*]\s+/.test(line)) {
        if (!inList) { out.push("<ul>"); inList = true; }
        out.push("<li>" + line.replace(/^\s*[-*]\s+/, "") + "</li>");
        continue;
      }
      if (inList) { out.push("</ul>"); inList = false; }
      if (/^#{1,6}\s+/.test(line)) {
        const level = line.match(/^#+/)[0].length;
        out.push(`<h${level}>${line.replace(/^#+\s+/, "")}</h${level}>`);
      } else if (line.trim() === "") {
        out.push("");
      } else {
        out.push(`<p>${line}</p>`);
      }
    }
    if (inList) out.push("</ul>");
    return out.join("");
  }

  async function boot() {
    const container = document.getElementById("articles");
    try {
      const filesRes = await fetch(`/api/list_files?folder=${encodeURIComponent(folder)}`);
      const files = await filesRes.json();

      const contents = await Promise.all(
        files.map(async (file) => {
          const fileName = file.split(".")[0];
          const res = await fetch(`/${folder}/${file}`);
          const text = await res.text();
          const lines = text.split("\n");
          const title = (lines[0] || "").replace(/#/g, "").trim();
          return { id: fileName, title, content: lines.slice(1).join("\n") };
        })
      );

      const urlParams = new URLSearchParams(window.location.search);
      const section = urlParams.get("section");

      container.innerHTML = contents
        .map(({ id, title, content }) => {
          const highlight = section === id ? "article highlighted" : "article";
          return `<div class="${highlight}" id="${escapeHtml(id)}">
            <div class="article-head">
              <span class="article-title" style="font-size:17px;">${escapeHtml(title)}</span>
            </div>
            <div class="article-content">${renderMarkdown(content)}</div>
          </div>`;
        })
        .join("");

      if (section) {
        const el = document.getElementById(section);
        if (el) {
          el.scrollIntoView({ behavior: "smooth" });
          el.classList.add("highlighted");
        }
      }
    } catch (error) {
      container.innerHTML = `<p>Error fetching files: ${escapeHtml(String(error))}</p>`;
    }
  }

  boot();
})();
