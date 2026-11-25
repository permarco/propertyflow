package com.pertegato.propertyflow.access.bootstrap;

import com.pertegato.propertyflow.access.service.StaffAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & staff-bootstrap")
class StaffBootstrapCommand implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffBootstrapCommand.class);

    private final StaffAccountService accounts;
    private final Environment configuration;

    StaffBootstrapCommand(StaffAccountService accounts, Environment configuration) {
        this.accounts = accounts;
        this.configuration = configuration;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!"none".equalsIgnoreCase(configuration.getProperty("spring.main.web-application-type"))) {
            throw new IllegalStateException("Die Einrichtung erfordert einen Serverbefehl ohne Webserver.");
        }
        String name = configuration.getProperty("propertyflow.staff.bootstrap.name");
        String email = configuration.getProperty("propertyflow.staff.bootstrap.email");
        boolean created = accounts.createInitialAdministrator(name, email);
        if (created) {
            LOGGER.info("Erstes Verwaltungskonto durch Servereinrichtung angelegt.");
        } else {
            LOGGER.info("Die Ersteinrichtung des Verwaltungskontos ist bereits abgeschlossen.");
        }
    }
}
