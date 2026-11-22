package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.McpScope;
import com.pertegato.propertyflow.access.web.McpConfirmationLogin;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.access.web.TenantWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Change;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.Status;
import com.pertegato.propertyflow.caseprocessing.contract.McpMessageConfirmations.View;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(McpMessageConfirmationWebTest.Web.class)
class McpMessageConfirmationWebTest {

    @Autowired
    private WebApplicationContext web;

    @Autowired
    private McpMessageConfirmations confirmations;

    @Test
    void rendersIntakeAndStaffApprovalAndRequiresBrowserCsrf() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).build();
        UUID tenant = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        UUID intake = UUID.randomUUID();
        UUID transition = UUID.randomUUID();
        String intakeUrl = McpConfirmationLogin.TENANT_PATH + intake;
        String transitionUrl = McpConfirmationLogin.STAFF_PATH + transition;
        MockHttpSession tenantSession = session(TenantWebSession.ACCOUNT, tenant);
        MockHttpSession staffSession = session(StaffWebSession.ACCOUNT, staff);

        mvc.perform(cookie(post(intakeUrl), tenantSession).param("csrf", "wrong").param("decision", "send"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(confirmations);

        when(confirmations.view(tenant, false, intake)).thenReturn(new View(intake, null, "Fenster", "Klemmt", false,
                Status.WAITING, null, McpScope.TENANT_INTAKE, "Wohnung 7"));
        when(confirmations.view(staff, true, transition)).thenReturn(new View(transition, "case-a", "Fenster", "", false,
                Status.WAITING, null, McpScope.STAFF_TRANSITIONS, Change.ABSCHLIESSEN.name()));
        mvc.perform(cookie(get(intakeUrl), tenantSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Meldung einreichen")))
                .andExpect(content().string(containsString("Wohnung 7")));
        mvc.perform(cookie(get(transitionUrl), staffSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fall abschliessen")))
                .andExpect(content().string(containsString("/mitarbeiter/faelle/case-a")));
        mvc.perform(cookie(post(transitionUrl), staffSession).param("csrf", "csrf-token").param("decision", "send"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(transitionUrl));
        verify(confirmations).decide(staff, true, transition, true);
    }

    private static MockHttpSession session(String attribute, UUID account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(attribute, account);
        session.setAttribute(StaffWebSession.CSRF, "csrf-token");
        return session;
    }

    private static MockHttpServletRequestBuilder cookie(MockHttpServletRequestBuilder request, MockHttpSession session) {
        return request.session(session).with(servlet -> {
            servlet.setRequestedSessionIdFromCookie(true);
            return servlet;
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Web {

        @Bean
        McpMessageConfirmations confirmations() {
            return mock(McpMessageConfirmations.class);
        }

        @Bean
        McpMessageConfirmationController controller(McpMessageConfirmations confirmations) {
            return new McpMessageConfirmationController(confirmations);
        }

        @Bean
        ThymeleafViewResolver thymeleaf(ApplicationContext applicationContext) {
            SpringResourceTemplateResolver templates = new SpringResourceTemplateResolver();
            templates.setApplicationContext(applicationContext);
            templates.setPrefix("classpath:/templates/");
            templates.setSuffix(".html");
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(templates);
            ThymeleafViewResolver resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
    }
}
