package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.CaseActionException;
import com.pertegato.propertyflow.caseprocessing.service.CaseOperationsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequiredArgsConstructor
public class CaseOperationsController {

    private final CaseOperationsService service;

    @GetMapping("/mitarbeiter/betrieb")
    String overview(HttpServletRequest request, HttpServletResponse response, Model model) {
        BrowserResponses.protect(response);
        HttpSession session = StaffWebSession.cookieSession(request);
        try {
            if (session == null || !(session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID actor)) {
                BrowserResponses.seeOther(request, response, StaffWebSession.LOGIN);
                return null;
            }
            model.addAttribute("csrf", session.getAttribute(StaffWebSession.CSRF));
            model.addAttribute("overview", service.read(new WebAccountContext(actor)));
        } catch (CaseActionException denied) {
            response.setStatus("UNAUTHENTICATED".equals(denied.code()) ? 401 : 403);
            model.addAttribute("error", "Sie haben keinen Zugriff auf diese Betriebsübersicht.");
        } catch (DataAccessException unavailable) {
            response.setStatus(503);
            model.addAttribute("error", "Der Auftragsstand konnte nicht geladen werden. Bitte versuchen Sie es später erneut.");
        } catch (IllegalStateException expired) {
            response.setStatus(401);
            model.addAttribute("error", "Bitte melden Sie sich erneut an.");
        }
        return "staff/operations";
    }
}
