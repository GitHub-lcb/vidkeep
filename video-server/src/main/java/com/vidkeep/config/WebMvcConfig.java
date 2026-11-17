package com.vidkeep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Web MVC 配置：CORS 白名单 + 本地 HLS/封面静态资源映射。
 *
 * <p>生产环境由 Nginx 直接分发 /hls 与 /covers（文档 4.1 / 8.5）；
 * 本地开发没有 Nginx 时，由后端把 DATA_DIR 下的 hls / covers 暴露为静态资源，保证播放链路可验证。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String allowedOrigins;
    private final Path dataDir;

    public WebMvcConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
                        @Value("${app.storage.data-dir:./data}") String dataDir) {
        this.allowedOrigins = allowedOrigins;
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 本地开发静态资源映射：/covers/** → DATA_DIR/covers/。
     *
     * <p>注意：/hls/** 不再走静态资源，由 {@code HlsStreamController} 统一做签名校验与 m3u8 改写（P1 防盗链）。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String baseUri = dataDir.toUri().toString();
        if (!baseUri.endsWith("/")) {
            baseUri = baseUri + "/";
        }
        registry.addResourceHandler("/covers/**").addResourceLocations(baseUri + "covers/");
    }
}
