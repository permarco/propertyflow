package com.pertegato.propertyflow.caseprocessing.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@Profile("frontend-demo")
public class DemoController {
    private final DemoCases cases;
    public DemoController(DemoCases cases) { this.cases = cases; }

    @ModelAttribute
    void common(Model model, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        var session = request.getSession();
        if (session.getAttribute("csrf") == null) session.setAttribute("csrf", UUID.randomUUID().toString());
        if (request.getMethod().equals("POST") && !session.getAttribute("csrf").equals(request.getParameter("csrf")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Die Anfrage konnte nicht bestätigt werden. Bitte die Seite neu laden.");
        model.addAttribute("csrf", session.getAttribute("csrf"));
        model.addAttribute("operation", UUID.randomUUID().toString());
        model.addAttribute("urgencies", DemoCases.URGENCIES);
        model.addAttribute("statuses", DemoCases.STATUSES);
        model.addAttribute("units", DemoCases.UNITS);
        model.addAttribute("staff", false);
        model.addAttribute("error", null);
    }
    @GetMapping("/")
    String home() { return "demo/home"; }

    @GetMapping("/mitarbeiter/faelle")
    String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String urgency,
                @RequestParam(defaultValue = "false") boolean closed, @RequestParam(defaultValue = "default") String sort,
                @RequestParam(defaultValue = "false") boolean desc, @RequestParam(defaultValue = "1") int page, Model model) {
        if (q.length() > 200 || (!urgency.isEmpty() && !DemoCases.URGENCIES.contains(urgency))
                || !List.of("default", "title", "unit", "received", "contact", "followUp", "urgency", "status").contains(sort))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Suchparameter.");
        model.addAttribute("staff", true);
        model.addAttribute("q", q); model.addAttribute("urgency", urgency); model.addAttribute("closed", closed);
        model.addAttribute("sort", sort); model.addAttribute("desc", desc);
        model.addAttribute("result", cases.list(q, urgency, closed, sort, desc, page));
        model.addAttribute("columns", List.of("Fall", "Objekt / Wohnung", "Eingang", "Letzte Aktion des Mieters", "Wiedervorlage", "Dringlichkeit", "Bearbeitungsstatus"));
        model.addAttribute("keys", List.of("title", "unit", "received", "contact", "followUp", "urgency", "status"));
        return "demo/list";
    }
    @GetMapping("/mitarbeiter/faelle/{id}")
    String detail(@PathVariable String id, Model model) {
        model.addAttribute("staff", true); model.addAttribute("c", cases.get(id));
        model.addAttribute("postUrl", "/mitarbeiter/faelle/" + id + "/nachrichten");
        return "demo/detail";
    }
    @GetMapping("/mieter/fall")
    String intake(Model model) {
        if (!model.containsAttribute("fields")) model.addAttribute("fields", Map.of("title", "", "unit", "", "email", "", "description", ""));
        return "demo/intake";
    }
    @GetMapping("/mieter/fall/zugang/{token}")
    String tenant(@PathVariable String token, Model model) {
        model.addAttribute("c", cases.tenant(token));
        model.addAttribute("postUrl", "/mieter/fall/zugang/" + token + "/nachrichten");
        return "demo/detail";
    }
    @PostMapping("/mieter/fall")
    Object create(@RequestParam Map<String, String> fields, Model model, HttpServletResponse response) {
        try {
            String token = cases.create(fields.get("title"), fields.get("unit"), fields.get("email"), fields.get("description"), fields.get("operation"));
            return redirect("/mieter/fall/zugang/" + token);
        } catch (ResponseStatusException ex) {
            response.setStatus(ex.getStatusCode().value()); model.addAttribute("error", ex.getReason()); model.addAttribute("fields", fields);
            model.addAttribute("operation", fields.get("operation")); return "demo/intake";
        }
    }
    @PostMapping({"/mitarbeiter/faelle/{id}/nachrichten", "/mieter/fall/zugang/{token}/nachrichten"})
    Object message(@PathVariable(required = false) String id, @PathVariable(required = false) String token,
                   @RequestParam String text, @RequestParam(defaultValue = "false") boolean internal,
                   @RequestParam(defaultValue = "false") boolean answer, @RequestParam String operation,
                   Model model, HttpServletResponse response) {
        try {
            cases.message(id, token, text, internal, answer, operation);
            return redirect(token == null ? "/mitarbeiter/faelle/" + id : "/mieter/fall/zugang/" + token);
        } catch (ResponseStatusException ex) {
            response.setStatus(ex.getStatusCode().value()); model.addAttribute("error", ex.getReason());
            model.addAttribute("draft", text); model.addAttribute("internal", internal); model.addAttribute("answer", answer);
            model.addAttribute("operation", operation);
            return token == null ? detail(id, model) : tenant(token, model);
        }
    }
    @PostMapping("/mitarbeiter/faelle/{id}/aktion")
    Object change(@PathVariable String id, @RequestParam String action, @RequestParam(defaultValue = "") String value,
                  @RequestParam(defaultValue = "") String days, @RequestParam(defaultValue = "") String reason,
                  @RequestParam long version, @RequestParam String operation,
                  Model model, HttpServletResponse response) {
        try {
            cases.change(id, action, action.equals("followUp") && !days.isBlank() ? "days:" + days : value, reason, version, operation);
            return redirect("/mitarbeiter/faelle/" + id);
        } catch (ResponseStatusException ex) {
            response.setStatus(ex.getStatusCode().value()); model.addAttribute("error", ex.getReason()); return detail(id, model);
        }
    }
    @GetMapping("/demo/datenschutz")
    String privacy() { return "demo/privacy"; }

    @ExceptionHandler(ResponseStatusException.class)
    String error(ResponseStatusException exception, Model model, HttpServletResponse response) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("staff", false);
        model.addAttribute("error", exception.getReason() == null ? "Diese Aktion ist nicht verfügbar." : exception.getReason());
        return "demo/error";
    }
    private RedirectView redirect(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return view;
    }
}
