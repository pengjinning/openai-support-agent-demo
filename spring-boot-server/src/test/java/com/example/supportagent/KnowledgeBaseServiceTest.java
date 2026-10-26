package com.example.supportagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.supportagent.model.kb.KBSearchResult;
import com.example.supportagent.service.KnowledgeBaseService;
import com.example.supportagent.tools.ToolCatalog;

class KnowledgeBaseServiceTest {

    private final KnowledgeBaseService service = new KnowledgeBaseService();

    @Test
    void searchReturnsRelevantResultsForReturnPolicy() {
        List<KBSearchResult> results = service.search("return policy", 6);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).filename()).isEqualToIgnoringCase("returns");
        assertThat(results.get(0).score()).isGreaterThan(0.5);
        assertThat(results.get(0).type()).isEqualTo("faq");
    }

    @Test
    void searchFiltersOutLowRelevance() {
        List<KBSearchResult> results = service.search("totally unrelated gibberish query", 6);
        assertThat(results).isEmpty();
    }

    @Test
    void listFilenamesReturnsKnowledgeBaseMarkdown() {
        List<String> names = service.listFilenames("knowledge_base");
        assertThat(names).contains("returns.md", "refunds.md");
    }

    @Test
    void toolCatalogClassifiesSensitiveTools() {
        assertThat(ToolCatalog.isAgentTool("cancel_order")).isTrue();
        assertThat(ToolCatalog.isAgentTool("search_knowledge_base")).isFalse();
        assertThat(ToolCatalog.isAgentTool("get_order")).isFalse();
    }
}
