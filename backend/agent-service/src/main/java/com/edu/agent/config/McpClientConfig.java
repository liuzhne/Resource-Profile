package com.edu.agent.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * MCP client 内部预共享 token 注入（A3 余项纵深防御）。
 *
 * <p>MCP server（8094 student-data / 8095 knowledge-rag）已绑 {@code 127.0.0.1} 网络隔离；本 customizer
 * 在其上加应用层凭证 —— 给所有 MCP 出站请求附 {@code X-MCP-Token} 头，与服务端校验配对，构成
 * 「网络隔离 + 预共享 token」纵深防御。Spring AI 1.1.6 的 streamable-http transport autoconfigure
 * 通过 {@code ObjectProvider<McpSyncHttpClientRequestCustomizer>} 自动挂上本 bean。
 *
 * <p>{@code educare.mcp.token} 为空（默认）→ 不附头，仅靠网络隔离，demo 默认链路字节零改动；
 * 三个进程（agent + 两个 server）配同一 token 即开启互验。
 */
@Configuration
public class McpClientConfig {

    @Value("${educare.mcp.token:}")
    private String mcpToken;

    @Bean
    public McpSyncHttpClientRequestCustomizer mcpTokenRequestCustomizer() {
        final String token = mcpToken == null ? "" : mcpToken.strip();
        return new McpSyncHttpClientRequestCustomizer() {
            @Override
            public void customize(HttpRequest.Builder builder, String method, URI uri,
                                  String body, McpTransportContext context) {
                if (!token.isEmpty()) {
                    builder.header("X-MCP-Token", token);
                }
            }
        };
    }
}
