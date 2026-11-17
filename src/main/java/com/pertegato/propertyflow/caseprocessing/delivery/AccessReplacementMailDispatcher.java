package com.pertegato.propertyflow.caseprocessing.delivery;

import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.Mail;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.SendResult;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseAccessAdministrationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.Recipient;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
@ConditionalOnProperty(name = "propertyflow.case-mail.enabled", havingValue = "true")
public class AccessReplacementMailDispatcher {
    private final CaseAccessAdministrationStore orders;
    private final MailDeliveryStore recipients;
    private final PersonalLinkCodec links;
    private final CaseMailSender sender;
    private final String baseUrl;

    public AccessReplacementMailDispatcher(CaseAccessAdministrationStore orders, MailDeliveryStore recipients,
            PersonalLinkCodec links, CaseMailSender sender, @Value("${propertyflow.public-base-url}") String baseUrl) {
        this.orders = orders;
        this.recipients = recipients;
        this.links = links;
        this.sender = sender;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    @Scheduled(fixedDelay = 2000)
    public void dispatch() {
        // Administrative Zugänge und ihr Versand sind auch ohne Camunda verfügbar.
        orders.claimMail().ifPresent(order -> {
            try {
                Recipient recipient = recipients.recipient(order.caseId()).orElseThrow();
                String token = links.restore(recipient.accessId(), recipient.encryptedToken());
                SendResult result = sender.send(new Mail(order.id(), UUID.randomUUID(), recipient.email(), order.caseId(),
                        "Ihr persönlicher Fall-Link wurde nach Prüfung ersetzt. Frühere Links sind nicht mehr gültig.",
                        baseUrl + "/mieter/fall/zugang/" + token));
                orders.mailResult(order.id(), switch (result) {
                    case ACCEPTED -> "SENT";
                    case RETRYABLE -> "PENDING";
                    case REJECTED -> "FAILED";
                    case UNKNOWN -> "UNCERTAIN";
                });
            } catch (RuntimeException exception) {
                // Kein automatischer Neuversand nach unklarem SMTP-Ausgang und keine Geheimnisse im Log.
                orders.mailResult(order.id(), "UNCERTAIN");
            }
        });
    }
}
