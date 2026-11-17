package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import com.pertegato.propertyflow.access.demo.DemoMcpGrants;
import com.pertegato.propertyflow.caseprocessing.demo.DemoCases;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
@Profile("frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.enabled", havingValue = "true")
class McpConfiguration implements WebMvcConfigurer {

    static final String TENANT_ID = McpConfiguration.class.getName() + ".tenantId";
    private static final Logger AUDIT = LoggerFactory.getLogger(McpConfiguration.class);
    private static final String ENDPOINT = "/mcp";
    private static final int MAX_REQUEST_BYTES = 8192;
    private static final Pattern LOCAL_HOST = Pattern.compile("(?:127\\.0\\.0\\.1|localhost)(?::[0-9]{1,5})?");
    private static final Pattern BEARER = Pattern.compile("(?i:Bearer) ([A-Za-z0-9_-]{43})");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                return true;
            }
        }).addPathPatterns(ENDPOINT);
    }

    @Bean
    WebMvcStatelessServerTransport mcpTransport() {
        return WebMvcStatelessServerTransport.builder()
                .messageEndpoint(ENDPOINT)
                .contextExtractor(request -> McpTransportContext.create(Map.of(TENANT_ID, request.attribute(TENANT_ID).orElseThrow())))
                .build();
    }

    @Bean
    McpStatelessSyncServer propertyFlowMcpServer(WebMvcStatelessServerTransport transport,
            DemoAccounts accounts, DemoCases cases) {
        FallstandTool fallstand = new FallstandTool(accounts, cases);
        return McpServer.sync(transport)
                .serverInfo("propertyflow", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .toolCall(fallstand.definition(), fallstand::call)
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> propertyFlowMcpRoutes(WebMvcStatelessServerTransport transport, DemoMcpGrants grants) {
        return transport.getRouterFunction().filter((request, handler) -> secure(request, handler, grants));
    }

    private ServerResponse secure(ServerRequest request, HandlerFunction<ServerResponse> handler, DemoMcpGrants grants) throws Exception {
        boolean isPost = HttpMethod.POST.equals(request.method());
        byte[] body = isPost ? readBody(request) : new byte[0];
        if (body.length > MAX_REQUEST_BYTES) {
            return reject(HttpStatus.CONTENT_TOO_LARGE);
        }

        String host = request.headers().firstHeader(HttpHeaders.HOST);
        List<String> origins = request.headers().header(HttpHeaders.ORIGIN);
        if (host == null || !LOCAL_HOST.matcher(host).matches()
                || !request.remoteAddress().map(address -> address.getAddress().isLoopbackAddress()).orElse(false)
                || origins.size() > 1 || (!origins.isEmpty() && !origins.getFirst().equals("http://" + host))) {
            return reject(HttpStatus.FORBIDDEN);
        }

        List<String> authorization = request.headers().header(HttpHeaders.AUTHORIZATION);
        Optional<UUID> tenantId = grants.findTenantId(bearerToken(authorization));
        if (tenantId.isEmpty()) {
            AUDIT.info("mcp_audit endpoint=/mcp outcome=unauthenticated");
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .header(HttpHeaders.CONNECTION, "close")
                    .contentLength(0)
                    .build();
        }
        ServerRequest.Builder checked = ServerRequest.from(request).attribute(TENANT_ID, tenantId.orElseThrow());
        if (isPost) {
            if (!request.headers().contentType().map(MediaType.APPLICATION_JSON::isCompatibleWith).orElse(false)) {
                return reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
            checked.body(body);
        }
        return handler.handle(checked.build());
    }

    private String bearerToken(List<String> authorization) {
        if (authorization.size() != 1) {
            return null;
        }
        Matcher matcher = BEARER.matcher(authorization.getFirst());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private byte[] readBody(ServerRequest request) throws IOException {
        return request.servletRequest().getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
    }

    private ServerResponse reject(HttpStatus status) {
        return ServerResponse.status(status).header(HttpHeaders.CONNECTION, "close").contentLength(0).build();
    }
}
