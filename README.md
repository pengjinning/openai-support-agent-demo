# Customer Support Agent with Human in the Loop Demo

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![NextJS](https://img.shields.io/badge/Built_with-NextJS-blue)
![Zhipu AI](https://img.shields.io/badge/Powered_by-Zhipu_AI_(GLM)-blue)

This repository contains a NextJS demo app of a Customer Service with a Human in the loop (HITL) use case, adapted to run on [Zhipu AI (智谱)](https://open.bigmodel.cn/) GLM models via the [OpenAI-compatible endpoint](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction).

It implements 2 views of a chat interface: one for the customer, and one for the human agent. The human agent is assisted by an AI agent to answer customer questions, while staying in control of sensitive actions.

![screenshot](./public/screenshot.jpg)

> **Note on this adaptation:** the original demo was built on the OpenAI Responses API with the hosted file search tool and vector stores. Since those services are OpenAI-only, this version replaces them with:
>
> - **Chat Completions** on Zhipu's OpenAI-compatible endpoint (`https://open.bigmodel.cn/api/paas/v4`), with a server-side adapter ([lib/zhipu-stream.ts](lib/zhipu-stream.ts)) that re-emits Responses-style streaming events so the client UI keeps working unchanged.
> - **Local knowledge base search** ([lib/kb-search.ts](lib/kb-search.ts)): markdown articles in `/public/knowledge_base` and `/public/faq` are embedded with Zhipu `embedding-3` and ranked by cosine similarity. It is exposed to the model as a `search_knowledge_base` function tool — no vector store setup needed.

Features:

- Multi-turn conversation handling
- Knowledge base search (local embeddings, no vector store to initialize)
- Knowledge base display
- Function calling with streaming
- Streaming suggested responses
- Suggested actions to execute tool calls
- Auto-execution of tool calls for non-sensitive actions

Feel free to customize this demo to suit your specific use case.

## How to use

1. **Set up a Zhipu AI account:**

   - Sign up on the [Zhipu open platform](https://open.bigmodel.cn/) and [create an API key](https://open.bigmodel.cn/usercenter/apikeys).
   - New accounts get free tokens, and free models (e.g. `glm-4.7-flash`) work with this demo.

2. **Clone the Repository:**

   ```bash
   git clone https://github.com/openai/openai-support-agent-demo.git
   ```

3. **Set the Zhipu API key:**

   2 options:

   - Set the `ZHIPUAI_API_KEY` environment variable globally in your system
   - Set the `ZHIPUAI_API_KEY` environment variable in the project: Create a `.env` file at the root of the project and add the following line (see `.env.example` for reference):

   ```bash
   ZHIPUAI_API_KEY=<your_api_key>
   ```

   Optional configuration:

   ```bash
   # Default: https://open.bigmodel.cn/api/paas/v4
   ZHIPU_BASE_URL=<zhipu-compatible-base-url>
   # Default: glm-4.7-flash (free); any GLM model with tool calling works, e.g. glm-5.3
   ZHIPU_MODEL=<model-name>
   ```

4. **Install dependencies:**

   Run in the project root:

   ```bash
   npm install
   ```

5. **Run the app:**

   ```bash
   npm run dev
   ```

   The app will be available at [`http://localhost:3010`](http://localhost:3010).

   No vector store initialization is needed: the knowledge base in `/public/knowledge_base` and `/public/faq` is embedded on first search (and re-embedded automatically when files change).

## Demo Flow

To try out the demo, you can ask questions that will trigger a knowledge base search.

Example questions:

- What is the return policy?
- How do I return a product?
- How can I cancel an order?

When an answer is generated, it will be displayed as a suggested response for the customer support representative.
In the agent view, you can edit the message or send it as is.

You can also click on the "Relevant articles" to see the corresponding articles in the knowledge base or FAQ.

You can then continue the conversation as the user.

You can ask for help to trigger actions.

Example questions:

- Help me cancel order ORD1001 => Should suggest the `cancel_order` action
- Help me reset my password => Should suggest the `reset_password` action
- Give me a list of my past orders => Should trigger the execution of `get_order_history`

### End-to-end demo flow

1. Ask as the user "How can I cancel my order?"
2. Confirm the suggested response
3. Ask as the user "Help me cancel order ORD1001"
4. Confirm the suggested response
5. Confirm the suggested action to cancel the order
6. Confirm the suggested response

### Limitations

Note that the functions that are executed are just placeholders and are not actually modifying any data, so the actions will not have any effect. For example, calling `cancel_order` won't change the status of the order.

Also note that the knowledge base search embeds each markdown article as a whole, which works well for the small demo articles. For larger documents, chunk them before embedding.

## Customization

To customize this demo you can:

- Edit prompts, initial message and model in `config/constants.ts` (or set `ZHIPU_MODEL`)
- Edit available functions in `config/tools-list.ts`
- Edit functions logic in `config/functions.ts`
- Edit knowledge base content in `/public/knowledge_base` and `/public/faq`
- (optional) Edit the demo data in `config/demoData.ts`

You can also customize the endpoints in the `/api` folder to call your own backend or external services.

If you want to use this code repository as a starting point for your own project in production, please note that this demo is not production-ready and that you would need to implement safety measures such as input guardrails, user authentication, etc.

## Contributing

You are welcome to open issues or submit PRs to improve this app, however, please note that we may not review all suggestions.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
