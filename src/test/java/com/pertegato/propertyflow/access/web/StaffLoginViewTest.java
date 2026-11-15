package com.pertegato.propertyflow.access.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import static org.assertj.core.api.Assertions.assertThat;

class StaffLoginViewTest {

    private static final String CONTEXT = "/propertyflow";
    private static final String CSRF = "test-csrf";
    private static final SpringTemplateEngine TEMPLATES = templateEngine();

    @Test
    void rendersStaffAddressStepWithContextRelativeFormAndNoRegistrationLink() {
        String html = render(false, null);

        assertThat(html).contains(
                "id=\"login-code-request\"",
                "action=\"/propertyflow/anmelden/code\"",
                "name=\"csrf\" value=\"test-csrf\"",
                "for=\"login-email\"",
                "autocomplete=\"email\"",
                "href=\"/propertyflow/css/propertyflow.css\"",
                "href=\"/propertyflow/mieter/fall\"")
                .doesNotContain("login-code-verify", "/registrieren", "/mcp-zugriff");
    }

    @Test
    void rendersNeutralCodeStepAndAccessibleEscapedError() {
        String html = render(true, "Fehler <script>alert(1)</script>");

        assertThat(html).contains(
                "role=\"status\"",
                "Wenn für diese Adresse ein berechtigtes Konto besteht, senden wir einen Einmalcode.",
                "id=\"login-code-verify\"",
                "action=\"/propertyflow/anmelden/pruefen\"",
                "action=\"/propertyflow/anmelden/code\"",
                "name=\"csrf\" value=\"test-csrf\"",
                "autocomplete=\"one-time-code\"",
                "aria-describedby=\"login-error\"",
                "Fehler &lt;script&gt;alert(1)&lt;/script&gt;",
                "href=\"/propertyflow/anmelden\"")
                .doesNotContain("<script>", "login-code-request", "/registrieren");
    }

    private static String render(boolean codeStep, String error) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", CONTEXT + "/anmelden");
        request.setContextPath(CONTEXT);
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, new MockHttpServletResponse()));
        context.setVariable("codeStep", codeStep);
        context.setVariable("csrf", CSRF);
        context.setVariable("error", error);
        return TEMPLATES.process("access/staff-login", context);
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
