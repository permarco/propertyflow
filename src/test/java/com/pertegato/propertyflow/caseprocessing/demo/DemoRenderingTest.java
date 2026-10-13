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
    @Autowired DemoCases cases;
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

    @Test void statusChangesPersistWithOptionalInternalReasonThroughHttp() throws Exception {
        String token = cases.create("Statusprüfung", "Testwohnung", "status@example.ch", "Test", "status-http-create");
        String id = cases.tenant(token).id();
        String path = "/mitarbeiter/faelle/" + id;
        var result = mvc.perform(get(path)).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession();
        String csrf = session.getAttribute("csrf").toString();
        String reason = "Interner Prüfvermerk <script>privat</script>";
        int entries = cases.get(id).entries().size();
        mvc.perform(post(path + "/aktion").session(session).param("csrf", csrf)
                .param("operation", "status-with-reason").param("version", Long.toString(cases.get(id).version()))
                .param("action", "status").param("value", "In Bearbeitung").param("reason", reason))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl(path));
        org.junit.jupiter.api.Assertions.assertEquals("In Bearbeitung", cases.get(id).status());
        org.junit.jupiter.api.Assertions.assertEquals(entries + 1, cases.get(id).entries().size());
        org.junit.jupiter.api.Assertions.assertEquals(reason, cases.get(id).entries().getLast().reason());
        mvc.perform(get(path)).andExpect(content().string(containsString("Interner Prüfvermerk &lt;script&gt;privat&lt;/script&gt;")));
        mvc.perform(get("/mieter/fall/zugang/" + token))
                .andExpect(content().string(containsString("In Bearbeitung")))
                .andExpect(content().string(not(containsString("Interner Prüfvermerk"))));
        mvc.perform(post(path + "/aktion").session(session).param("csrf", csrf)
                .param("operation", "status-without-reason").param("version", Long.toString(cases.get(id).version()))
                .param("action", "status").param("value", "Mitarbeiterprüfung erforderlich"))
                .andExpect(status().isSeeOther());
        org.junit.jupiter.api.Assertions.assertEquals("Mitarbeiterprüfung erforderlich", cases.get(id).status());
        org.junit.jupiter.api.Assertions.assertEquals("", cases.get(id).entries().getLast().reason());
    }

    @Test void listContextSurvivesDetailAndSavingAndRejectsExternalReturnTarget() throws Exception {
        String back = "/mitarbeiter/faelle?q=Heizung&urgency=Hoch&closed=true&sort=title&desc=true&page=1";
        mvc.perform(get("/mitarbeiter/faelle").param("q", "Heizung").param("urgency", "Hoch")
                .param("closed", "true").param("sort", "title").param("desc", "true"))
                .andExpect(content().string(containsString("returnTo=")));
        String id = cases.tenant(cases.create("Rückkehrtest", "Wohnung", "back@example.ch", "Test", "back-create")).id();
        String path = "/mitarbeiter/faelle/" + id;
        var response = mvc.perform(get(path).param("returnTo", back))
                .andExpect(content().string(containsString("href=\"" + back.replace("&", "&amp;") + "\""))).andReturn();
        var session = (MockHttpSession) response.getRequest().getSession();
        var saved = mvc.perform(post(path + "/aktion").session(session)
                .param("csrf", session.getAttribute("csrf").toString()).param("returnTo", back)
                .param("operation", "back-status").param("version", Long.toString(cases.get(id).version()))
                .param("action", "status").param("value", "In Bearbeitung"))
                .andExpect(status().isSeeOther()).andReturn();
        mvc.perform(get(java.net.URI.create(saved.getResponse().getRedirectedUrl())).session(session))
                .andExpect(content().string(containsString("href=\"" + back.replace("&", "&amp;") + "\"")));
        for (String invalid : new String[]{"https://example.com", "//example.com/mitarbeiter/faelle", "/mieter/fall"}) {
            mvc.perform(get(path).param("returnTo", invalid))
                    .andExpect(content().string(containsString("href=\"/mitarbeiter/faelle\" class=\"button secondary\">Zur Fallübersicht")));
        }
        mvc.perform(get(path)).andExpect(content().string(containsString("href=\"/mitarbeiter/faelle\" class=\"button secondary\">Zur Fallübersicht")));
    }
}
