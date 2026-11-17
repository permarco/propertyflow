package com.pertegato.propertyflow.access.web;

import com.pertegato.propertyflow.access.service.AccountAdministrationService;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = RegularStaffLoginController.class)
@Profile("propertyflow-backend & propertyflow-dev & !frontend-demo")
public class DevelopmentLoginAdvice {
    private final AccountAdministrationService accounts;

    public DevelopmentLoginAdvice(AccountAdministrationService accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute
    void developmentAccount(Model model) {
        model.addAttribute("developmentStaffEmail", accounts.developmentEmail());
    }
}
