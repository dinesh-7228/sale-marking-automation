package com.countrydelight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // static/ takes precedence; templates/ is only a fallback and must not
        // shadow the real static assets (index.html).
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/templates/");
    }
}
