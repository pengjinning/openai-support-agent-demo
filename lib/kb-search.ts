import { promises as fs } from "fs";
import path from "path";
import { getZhipuClient } from "./zhipu";

/**
 * Local knowledge base search, replacing the OpenAI hosted vector store /
 * file search tool (not available on Zhipu).
 *
 * All markdown articles in /public/knowledge_base and /public/faq are embedded
 * once with the Zhipu `embedding-3` model and cached in memory. Queries are
 * embedded and ranked by cosine similarity.
 */

export interface KBSearchResult {
  text: string;
  score: number;
  attributes: {
    type: string;
    filename: string;
    filepath: string;
  };
}

interface KBEntry extends KBSearchResult {
  embedding: number[];
}

const KB_FOLDERS = ["knowledge_base", "faq"];
const EMBEDDING_MODEL = "embedding-3";

let cache: { signature: string; entries: KBEntry[] } | null = null;

const cosineSimilarity = (a: number[], b: number[]) => {
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  return dot / (Math.sqrt(normA) * Math.sqrt(normB));
};

async function loadFiles(): Promise<
  { folder: string; filename: string; filepath: string; text: string; mtimeMs: number }[]
> {
  const publicDir = path.join(process.cwd(), "public");
  const files: {
    folder: string;
    filename: string;
    filepath: string;
    text: string;
    mtimeMs: number;
  }[] = [];
  for (const folder of KB_FOLDERS) {
    const dir = path.join(publicDir, folder);
    let names: string[] = [];
    try {
      names = await fs.readdir(dir);
    } catch {
      continue;
    }
    for (const name of names.filter((n) => n.endsWith(".md"))) {
      const full = path.join(dir, name);
      const [text, stat] = await Promise.all([
        fs.readFile(full, "utf-8"),
        fs.stat(full),
      ]);
      files.push({
        folder,
        filename: name.replace(/\.md$/, ""),
        filepath: `/${folder}/${name}`,
        text,
        mtimeMs: stat.mtimeMs,
      });
    }
  }
  return files;
}

async function getEntries(): Promise<KBEntry[]> {
  const files = await loadFiles();
  const signature = files.map((f) => `${f.filepath}:${f.mtimeMs}`).join("|");
  if (cache && cache.signature === signature) {
    return cache.entries;
  }

  const client = getZhipuClient();
  const response = await client.embeddings.create({
    model: EMBEDDING_MODEL,
    input: files.map((f) => f.text),
  });

  const entries: KBEntry[] = files.map((file, i) => ({
    text: file.text,
    score: 0,
    attributes: {
      type: file.folder,
      filename: file.filename,
      filepath: file.filepath,
    },
    embedding: response.data[i].embedding,
  }));
  cache = { signature, entries };
  return entries;
}

export async function searchKnowledgeBase(
  query: string,
  topK = 6
): Promise<KBSearchResult[]> {
  const [entries, client] = await Promise.all([getEntries(), getZhipuClient()]);
  const queryResponse = await client.embeddings.create({
    model: EMBEDDING_MODEL,
    input: query,
  });
  const queryEmbedding = queryResponse.data[0].embedding;

  const ranked = entries
    .map((entry) => ({
      ...entry,
      score: cosineSimilarity(queryEmbedding, entry.embedding),
    }))
    .sort((a, b) => b.score - a.score)
    .slice(0, topK);

  // Normalize scores within the result set so the client-side relevance
  // threshold (score > 0.5, see useDataStore) stays meaningful regardless of
  // the embedding model's raw similarity range.
  const max = ranked[0]?.score ?? 0;
  const min = ranked[ranked.length - 1]?.score ?? 0;
  return ranked
    .map((entry) => ({
      text: entry.text,
      score: max === min ? 1 : (entry.score - min) / (max - min),
      attributes: entry.attributes,
    }))
    .filter((entry) => entry.score > 0.5);
}
