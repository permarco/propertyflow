package com.pertegato.propertyflow.caseprocessing.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@ActiveProfiles("frontend-demo")
class DemoRenderingTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }
    @Test void allScreensRenderWithoutDatabaseAndTenantHasNoMemoOrStream() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Frontend")));
        mvc.perform(get("/mieter/fall")).andExpect(status().isOk()).andExpect(content().string(containsString("Neues Anliegen melden")));
        mvc.perform(get("/mitarbeiter/faelle")).andExpect(status().isOk()).andExpect(content().string(containsString("Fallübersicht")));
        mvc.perform(get("/mitarbeiter/faelle/REQ-2026-001")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hauswart telefonisch"))).andExpect(content().string(containsString("KI-Vorschlag")));
        mvc.perform(get("/mieter/fall/zugang/demo-aktiver-fall")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Hauswart telefonisch"))))
                .andExpect(content().string(not(containsString("KI-Vorschlag"))))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/mieter/fall/zugang/demo-abgeschlossener-fall")).andExpect(status().isOk());
        mvc.perform(get("/mieter/fall/zugang/ungueltig")).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Heizung"))));
    }
    @Test void csrfRequiredAndHtmlIsEscapedAfterPostRedirectGet() throws Exception {
        mvc.perform(post("/mieter/fall")).andExpect(status().isForbidden());
        var result = mvc.perform(get("/mieter/fall")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession();
        var created = mvc.perform(post("/mieter/fall").session(session)
                .param("csrf", session.getAttribute("csrf").toString()).param("operation", "render-xss")
                .param("title", "<script>alert(1)</script>").param("unit", "Testwohnung")
                .param("email", "test@example.ch").param("description", "<img src=x onerror=alert(1)>"))
                .andExpect(status().isSeeOther()).andReturn();
        mvc.perform(get(created.getResponse().getRedirectedUrl()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<img src=x"))));
    }
}
