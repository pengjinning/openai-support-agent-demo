import OpenAI from "openai";

/**
 * Shared Zhipu AI (智谱) client.
 *
 * Zhipu exposes an OpenAI-compatible endpoint, so we reuse the `openai` SDK
 * and only point it at the Zhipu base URL with the Zhipu API key.
 * See https://docs.bigmodel.cn/cn/guide/develop/openai/introduction
 */
export const ZHIPU_BASE_URL =
  process.env.ZHIPU_BASE_URL ?? "https://open.bigmodel.cn/api/paas/v4";

export function getZhipuClient(): OpenAI {
  const apiKey = process.env.ZHIPUAI_API_KEY;
  if (!apiKey) {
    throw new Error(
      "Missing ZHIPUAI_API_KEY environment variable. Get a key at https://open.bigmodel.cn/usercenter/apikeys and add it to your .env file."
    );
  }
  return new OpenAI({
    apiKey,
    baseURL: ZHIPU_BASE_URL,
  });
}
