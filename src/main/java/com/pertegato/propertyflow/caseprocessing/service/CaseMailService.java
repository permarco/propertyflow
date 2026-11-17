package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.Mail;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.SendResult;
import com.pertegato.propertyflow.caseprocessing.delivery.PersonalLinkCodec;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.Delivery;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore.Recipient;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
public class CaseMailService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofMinutes(2);
    private final MailDeliveryStore store;
    private final PersonalLinkCodec links;
    private final CaseMailSender sender;
    private final TransactionTemplate writes;
    private final String baseUrl;

    public CaseMailService(MailDeliveryStore store, PersonalLinkCodec links, CaseMailSender sender,
            PlatformTransactionManager transactions,
            @Value("${propertyflow.public-base-url:http://localhost:8081}") String baseUrl) {
        URI base = URI.create(baseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("A trusted HTTP(S) public base URL is required");
        }
        this.store = store;
        this.links = links;
        this.sender = sender;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.writes = new TransactionTemplate(transactions);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String prepare(UUID id, long instanceKey, long definitionKey, String tenantId) {
        return writes.execute(transaction -> {
            Delivery delivery = store.lock(id);
            require(Objects.equals(delivery.definitionKey(), definitionKey) && tenantId.equals(delivery.tenantId()), "WRONG_MAIL_DEFINITION");
            if (delivery.instanceKey() != null) {
                require(delivery.instanceKey() == instanceKey, "OTHER_MAIL_INSTANCE");
            } else {
                store.register(id, instanceKey);
            }
            if ("SENT".equals(delivery.state())) {
                return "SKIP";
            }
            return switch (delivery.type()) {
                case "ACCEPTED", "TENANT_MESSAGE", "STAFF_MESSAGE" -> "MESSAGE";
                case "STATUS_CHANGED", "URGENCY_CHANGED" -> "CHANGE";
                default -> throw new WorkflowRejectedException("UNKNOWN_MAIL_TYPE");
            };
        });
    }

    public MailOutcome send(UUID id, long instanceKey) {
        Attempt attempt = writes.execute(transaction -> begin(id, instanceKey));
        if (attempt.mail() == null) {
            return attempt.outcome();
        }
        SendResult result = sender.send(attempt.mail());
        return writes.execute(transaction -> {
            Delivery current = owned(id, instanceKey);
            require(attempt.mail().attemptId().equals(current.attemptId()), "OTHER_MAIL_ATTEMPT");
            if ("SENT".equals(current.state())) {
                return MailOutcome.SENT;
            }
            if (result == SendResult.ACCEPTED) {
                store.result(id, "SENT", null);
                return MailOutcome.SENT;
            }
            if ("UNCERTAIN".equals(current.state()) || result == SendResult.UNKNOWN) {
                store.result(id, "UNCERTAIN", "SMTP_OUTCOME_UNKNOWN");
                return MailOutcome.REVIEW;
            }
            if (result == SendResult.REJECTED || current.attempts() >= MAX_ATTEMPTS) {
                store.result(id, "FAILED", result == SendResult.REJECTED ? "SMTP_REJECTED" : "SMTP_RETRIES_EXHAUSTED");
                return MailOutcome.REVIEW;
            }
            store.result(id, "PENDING", "SMTP_TEMPORARILY_UNAVAILABLE");
            return MailOutcome.RETRY;
        });
    }

    public void recordProblem(UUID id, long instanceKey) {
        writes.executeWithoutResult(transaction -> {
            store.lockCaseForDelivery(id);
            Delivery delivery = owned(id, instanceKey);
            if ("SENT".equals(delivery.state())) {
                return;
            }
            require("FAILED".equals(delivery.state()) || "UNCERTAIN".equals(delivery.state()), "NO_MAIL_PROBLEM");
            store.recordProblem(delivery);
        });
    }

    private Attempt begin(UUID id, long instanceKey) {
        Delivery delivery = owned(id, instanceKey);
        if ("SENT".equals(delivery.state())) {
            return new Attempt(MailOutcome.SENT, null);
        }
        if ("FAILED".equals(delivery.state()) || "UNCERTAIN".equals(delivery.state())) {
            return new Attempt(MailOutcome.REVIEW, null);
        }
        if ("SENDING".equals(delivery.state())) {
            if (delivery.attemptStartedAt().plus(ATTEMPT_TIMEOUT).isBefore(Instant.now())) {
                store.result(id, "UNCERTAIN", "SMTP_ATTEMPT_INTERRUPTED");
                return new Attempt(MailOutcome.REVIEW, null);
            }
            return new Attempt(MailOutcome.BUSY, null);
        }
        String information = information(delivery);
        Recipient recipient = store.recipient(delivery.caseId()).orElse(null);
        if (recipient == null) {
            store.result(id, "FAILED", "CURRENT_LINK_UNAVAILABLE");
            return new Attempt(MailOutcome.REVIEW, null);
        }
        String token;
        try {
            token = links.restore(recipient.accessId(), recipient.encryptedToken());
        } catch (IllegalStateException exception) {
            store.result(id, "FAILED", "CURRENT_LINK_UNAVAILABLE");
            return new Attempt(MailOutcome.REVIEW, null);
        }
        UUID attemptId = UUID.randomUUID();
        store.begin(id, attemptId, recipient.email());
        Mail mail = new Mail(id, attemptId, recipient.email(), delivery.caseId(), information,
                baseUrl + "/mieter/fall/zugang/" + token);
        return new Attempt(null, mail);
    }

    private String information(Delivery delivery) {
        if ("STAFF_MESSAGE".equals(delivery.type())) {
            return store.messageRequiresAnswer(delivery.id())
                    ? "Ihre Antwort ist erforderlich. Bitte öffnen Sie Ihren Fall und antworten Sie dort auf die neue Rückfrage."
                    : "Eine neue Nachricht Ihrer Immobilienverwaltung ist im Fallverlauf verfügbar.";
        }
        if ("TENANT_MESSAGE".equals(delivery.type())) {
            return "Ihre Nachricht wurde im Fallverlauf gespeichert.";
        }
        if ("ACCEPTED".equals(delivery.type())) {
            return "Ihr Anliegen wurde gespeichert.";
        }
        if ("URGENCY_CHANGED".equals(delivery.type())) {
            String label = switch (store.eventUrgency(delivery.id())) {
                case "CRITICAL" -> "Kritisch";
                case "HIGH" -> "Hoch";
                case "NORMAL" -> "Normal";
                case "LOW" -> "Niedrig";
                default -> throw new WorkflowRejectedException("INVALID_URGENCY_EVENT");
            };
            return "Die Dringlichkeit Ihres Falls wurde geändert: " + label + ".";
        }
        require("STATUS_CHANGED".equals(delivery.type()), "UNKNOWN_MAIL_TYPE");
        String status = store.eventStatus(delivery.id());
        String label = switch (status) {
            case "UNDER_CLARIFICATION" -> "In Abklärung";
            case "WAITING_FOR_TENANT" -> "Wartet auf Mieterantwort";
            case "STAFF_REVIEW_REQUIRED" -> "Mitarbeiterprüfung erforderlich";
            case "IN_PROGRESS" -> "In Bearbeitung";
            case "WAITING_FOR_EXTERNAL" -> "Wartet auf externe Bearbeitung";
            case "WAITING_FOR_FOLLOW_UP" -> "Wartet auf Wiedervorlage";
            default -> throw new WorkflowRejectedException("STATUS_HAS_NO_MAIL_NOTIFICATION");
        };
        return "Der Status Ihres Falls wurde geändert: " + label + ".";
    }

    private Delivery owned(UUID id, long instanceKey) {
        Delivery delivery = store.lock(id);
        require(delivery.instanceKey() != null && delivery.instanceKey() == instanceKey, "UNREGISTERED_MAIL_INSTANCE");
        return delivery;
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new WorkflowRejectedException(reason);
        }
    }

    public enum MailOutcome {
        SENT,
        RETRY,
        BUSY,
        REVIEW
    }

    private record Attempt(MailOutcome outcome, Mail mail) {
    }
}
