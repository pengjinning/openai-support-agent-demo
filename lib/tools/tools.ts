import { toolsList } from "../../config/tools-list";

// Tool definitions sent to the model. `search_knowledge_base` is a regular
// function tool backed by local Zhipu-embedding search (lib/kb-search.ts),
// replacing the OpenAI hosted file_search tool.
export const tools = toolsList.map((tool) => {
  const toolDef: {
    type: string;
    name: string;
    parameters: any;
    strict: boolean;
    description?: string;
  } = {
    type: "function",
    name: tool.name,
    parameters: {
      type: "object",
      properties: { ...tool.parameters },
      required: Object.keys(tool.parameters),
      additionalProperties: false,
    },
    strict: true,
  };
  if ((tool as any).description) {
    toolDef.description = (tool as any).description;
  }
  return toolDef;
});
