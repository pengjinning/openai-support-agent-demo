import { searchKnowledgeBase } from "@/lib/kb-search";
import { NextResponse } from "next/server";

/**
 * Server-side knowledge base search used by the `search_knowledge_base` tool
 * (replaces the OpenAI hosted file search, which Zhipu does not offer).
 */
export async function POST(request: Request) {
  try {
    const { query } = await request.json();
    if (!query || typeof query !== "string") {
      return NextResponse.json(
        { error: "Missing or invalid 'query' in request body" },
        { status: 400 }
      );
    }
    const results = await searchKnowledgeBase(query);
    return NextResponse.json({ results });
  } catch (error) {
    console.error("Error searching knowledge base:", error);
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unknown error",
      },
      { status: 500 }
    );
  }
}
