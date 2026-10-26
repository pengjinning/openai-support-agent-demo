package com.example.supportagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the knowledge base and FAQ markdown files under their public paths so
 * the article pages and the search service read them from the same locations as
 * the original NextJS {@code /public} layout.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/knowledge_base/**")
                .addResourceLocations("classpath:/knowledge_base/");
        registry.addResourceHandler("/faq/**")
                .addResourceLocations("classpath:/faq/");
    }
}
