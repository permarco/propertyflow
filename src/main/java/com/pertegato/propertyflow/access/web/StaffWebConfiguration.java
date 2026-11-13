package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Profile("frontend-demo")
class StaffWebConfiguration implements WebMvcConfigurer, HandlerInterceptor {

    private final DemoAccounts accounts;

    StaffWebConfiguration(DemoAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/mitarbeiter/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        StaffWebSession.protectResponse(response);
        boolean safeRead = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        boolean navigation = safeRead && !"partial".equals(request.getHeader("X-PropertyFlow-Request"))
                && !"empty".equals(request.getHeader("Sec-Fetch-Dest"));
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID id) {
            if (accounts.findActiveById(id).filter(account -> !account.tenant()).isPresent()) {
                return true;
            }
            response.setHeader("X-PropertyFlow-Access", "denied");
            response.setStatus(HttpStatus.FORBIDDEN.value());
            if (navigation) {
                response.setContentType("text/plain;charset=UTF-8");
                if (HttpMethod.GET.matches(request.getMethod())) {
                    response.getWriter().write("Sie haben keinen Zugriff auf den Mitarbeiterbereich.");
                }
            }
            return false;
        }

        if (safeRead) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String query = request.getQueryString();
            String target = StaffWebSession.safeTarget(query == null ? path : path + "?" + query);
            request.getSession().setAttribute(StaffWebSession.RETURN_TO, target);
            if (navigation) {
                StaffWebSession.redirect(request, response, StaffWebSession.LOGIN);
                return false;
            }
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return false;
    }
}
