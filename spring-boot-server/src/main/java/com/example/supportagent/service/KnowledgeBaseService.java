package com.example.supportagent.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.example.supportagent.model.kb.KBSearchResult;

/**
 * Local knowledge base search replacing the OpenAI hosted vector store / file
 * search tool (which DeepSeek does not offer).
 *
 * <p>All markdown articles in {@code classpath:/knowledge_base} and
 * {@code classpath:/faq} are indexed with a simple BM25 lexical ranking. The
 * result scores are min-max normalized to {@code [0, 1]} and filtered with the
 * same {@code score > 0.5} threshold used by the original client.
 */
@Service
public class KnowledgeBaseService {

    private static final String[] FOLDERS = { "knowledge_base", "faq" };
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private record Entry(String text, String type, String filename, String filepath) {
    }

    private record ScoredEntry(Entry entry, double score) {
    }

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Lists the markdown filenames in a folder (mirrors the original
     * {@code /api/list_files} endpoint, which returned raw filenames).
     */
    public List<String> listFilenames(String folder) {
        List<String> names = new ArrayList<>();
        if (folder == null || folder.isBlank()) {
            return names;
        }
        String pattern = "classpath:" + folder + "/*.md";
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                String filename = resource.getFilename();
                if (filename != null) {
                    names.add(filename);
                }
            }
        } catch (IOException e) {
            // Folder not found; return empty list.
        }
        names.sort(String::compareTo);
        return names;
    }

    public List<KBSearchResult> search(String query, int topK) {
        List<Entry> entries = loadEntries();
        if (entries.isEmpty() || isBlank(query)) {
            return List.of();
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        // Document frequency across the corpus for IDF.
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Entry entry : entries) {
            for (String term : new java.util.LinkedHashSet<>(tokenize(entry.text()))) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        // Average document length for BM25 normalization.
        double totalLength = entries.stream().mapToInt(e -> tokenize(e.text()).size()).sum();
        double avgLength = totalLength / entries.size();

        List<ScoredEntry> scored = new ArrayList<>();
        for (Entry entry : entries) {
            List<String> docTerms = tokenize(entry.text());
            Map<String, Integer> tf = termFrequency(docTerms);
            double score = 0;
            for (String term : queryTerms) {
                Integer freq = tf.get(term);
                if (freq == null) {
                    continue;
                }
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (entries.size() - df + 0.5) / (df + 0.5));
                double numerator = freq * (K1 + 1);
                double denominator = freq + K1 * (1 - B + B * (docTerms.size() / avgLength));
                score += idf * (numerator / denominator);
            }
            scored.add(new ScoredEntry(entry, score));
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        // Min-max normalize the scores within the result set so the client-side
        // threshold (score > 0.5) stays meaningful regardless of raw BM25 range.
        double max = scored.isEmpty() ? 0 : scored.get(0).score();
        double min = scored.isEmpty() ? 0 : scored.get(scored.size() - 1).score();
        if (max == 0) {
            // No query term matched any document.
            return List.of();
        }
        List<ScoredEntry> top = scored.stream().limit(topK).toList();

        List<KBSearchResult> results = new ArrayList<>();
        for (ScoredEntry s : top) {
            double normalized = max == min ? 1.0 : (s.score() - min) / (max - min);
            if (normalized <= 0.5) {
                continue;
            }
            results.add(new KBSearchResult(s.entry().text(), normalized,
                    s.entry().type(), s.entry().filename(), s.entry().filepath()));
        }
        return results;
    }

    private List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();
        for (String folder : FOLDERS) {
            String pattern = "classpath:" + folder + "/*.md";
            try {
                for (Resource resource : resolver.getResources(pattern)) {
                    String filename = resource.getFilename();
                    if (filename == null) {
                        continue;
                    }
                    String text = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String name = filename.replaceAll("\\.md$", "");
                    entries.add(new Entry(text, folder, name, "/" + folder + "/" + filename));
                }
            } catch (IOException e) {
                // Folder may not exist in some packaging modes; skip it.
            }
        }
        return entries;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!raw.isBlank()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static Map<String, Integer> termFrequency(List<String> terms) {
        Map<String, Integer> tf = new HashMap<>();
        for (String term : terms) {
            tf.merge(term, 1, Integer::sum);
        }
        return tf;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
