package com.pertegato.propertyflow.caseprocessing.mcp;

import com.pertegato.propertyflow.access.contract.McpAccountContext;
import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.contract.McpTenantAuthorization;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.mcp.backend.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class BackendMcpConfiguration implements WebMvcConfigurer {

    static final String ACCOUNT_CONTEXT = BackendMcpConfiguration.class.getName() + ".accountContext";
    private static final String ENDPOINT = "/mcp";
    private static final int MAX_REQUEST_BYTES = 32768;
    private static final Pattern BEARER = Pattern.compile("(?i:Bearer) ([A-Za-z0-9._~+/=-]{1,4096})");

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
    WebMvcStatelessServerTransport backendMcpTransport() {
        return WebMvcStatelessServerTransport.builder()
                .messageEndpoint(ENDPOINT)
                .contextExtractor(request -> McpTransportContext.create(
                        Map.of(ACCOUNT_CONTEXT, request.attribute(ACCOUNT_CONTEXT).orElseThrow())))
                .build();
    }

    @Bean
    McpStatelessSyncServer backendMcpServer(WebMvcStatelessServerTransport transport) {
        return McpServer.sync(transport)
                .serverInfo(BackendMcpProtocol.SERVER_NAME, BackendMcpProtocol.SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> backendMcpRoutes(WebMvcStatelessServerTransport transport, McpTenantAuthorization authorization,
            McpCaseReading cases, McpMessageConfirmations confirmations) {
        BackendMcpProtocol protocol = new BackendMcpProtocol(new BackendMcpTools(authorization, cases),
                new BackendMcpMessageTools(authorization, confirmations));
        return transport.getRouterFunction().filter((request, handler) -> secure(request, handler, authorization, protocol));
    }

    private ServerResponse secure(ServerRequest request, HandlerFunction<ServerResponse> handler, McpTenantAuthorization authorization,
            BackendMcpProtocol protocol)
            throws Exception {
        List<String> origins = request.headers().header(HttpHeaders.ORIGIN);
        URI resource = authorization.resourceUri();
        String expectedOrigin = resource.getScheme() + "://" + resource.getRawAuthority();
        if (origins.size() > 1 || (!origins.isEmpty() && !expectedOrigin.equals(origins.getFirst()))) {
            return reject(HttpStatus.FORBIDDEN);
        }
        Optional<McpAccountContext> account = bearer(request.headers().header(HttpHeaders.AUTHORIZATION))
                .flatMap(authorization::authenticate);
        if (account.isEmpty()) {
            if (HttpMethod.POST.equals(request.method()) && json(request)) {
                byte[] attempted = readBody(request);
                if (attempted.length <= MAX_REQUEST_BYTES) {
                    protocol.auditAnonymous(attempted);
                }
            }
            String challenge = "Bearer resource_metadata=\"" + authorization.resourceMetadataUri().toASCIIString()
                    + "\", scope=\"" + McpScope.TENANT_CASES.value() + "\"";
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentLength(0)
                    .build();
        }
        ServerRequest.Builder checked = ServerRequest.from(request).attribute(ACCOUNT_CONTEXT, account.orElseThrow());
        if (HttpMethod.POST.equals(request.method())) {
            if (!json(request)) {
                return reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
            byte[] body = readBody(request);
            if (body.length > MAX_REQUEST_BYTES) {
                return reject(HttpStatus.CONTENT_TOO_LARGE);
            }
            ServerResponse handled = protocol.handle(request, body, account.orElseThrow());
            if (handled != null) {
                return handled;
            }
            checked.body(body);
        }
        return handler.handle(checked.build());
    }

    private static Optional<String> bearer(List<String> headers) {
        if (headers.size() != 1) {
            return Optional.empty();
        }
        Matcher match = BEARER.matcher(headers.getFirst());
        return match.matches() ? Optional.of(match.group(1)) : Optional.empty();
    }

    private static boolean json(ServerRequest request) {
        return request.headers().contentType().map(MediaType.APPLICATION_JSON::isCompatibleWith).orElse(false);
    }

    private static byte[] readBody(ServerRequest request) throws IOException {
        return request.servletRequest().getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
    }

    private static ServerResponse reject(HttpStatus status) {
        return ServerResponse.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(0)
                .build();
    }
}
