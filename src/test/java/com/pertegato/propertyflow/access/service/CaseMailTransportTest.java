package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.Mail;
import com.pertegato.propertyflow.caseprocessing.delivery.CaseMailSender.SendResult;
import com.pertegato.propertyflow.caseprocessing.delivery.SmtpCaseMailSender;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseMailTransportTest {

    @Test
    void actualSmtpMessageUsesStableIdAndContainsOnlyTheNotificationAndPersonalLink() throws Exception {
        try (SmtpDeliveryTest.SmtpPeer peer = new SmtpDeliveryTest.SmtpPeer(SmtpDeliveryTest.Mode.ACCEPT)) {
            Mail mail = mail();
            assertEquals(SendResult.ACCEPTED, sender(peer.port()).send(mail));
            MimeMessage actual = peer.message();
            assertEquals("<" + mail.orderId() + "@propertyflow>", actual.getMessageID());
            assertEquals("recipient@example.test", actual.getAllRecipients()[0].toString());
            String body = (String) actual.getContent();
            assertTrue(body.contains(mail.personalUrl()));
            assertTrue(body.contains("vertraulich"));
            assertFalse(actual.getSubject().contains(mail.caseId()));
        }
    }

    @Test
    void transportDistinguishesRefusalRejectionAndUncertainTimeout() throws Exception {
        for (SmtpDeliveryTest.Mode mode : new SmtpDeliveryTest.Mode[] {
                SmtpDeliveryTest.Mode.REFUSED, SmtpDeliveryTest.Mode.REJECT, SmtpDeliveryTest.Mode.TIMEOUT}) {
            try (SmtpDeliveryTest.SmtpPeer peer = new SmtpDeliveryTest.SmtpPeer(mode)) {
                SendResult expected = switch (mode) {
                    case REFUSED -> SendResult.RETRYABLE;
                    case REJECT -> SendResult.REJECTED;
                    default -> SendResult.UNKNOWN;
                };
                assertEquals(expected, sender(peer.port()).send(mail()), mode.name());
            }
        }
    }

    private SmtpCaseMailSender sender(int port) {
        JavaMailSenderImpl transport = new JavaMailSenderImpl();
        transport.setHost("127.0.0.1");
        transport.setPort(port);
        transport.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "500");
        transport.getJavaMailProperties().setProperty("mail.smtp.timeout", "500");
        transport.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "500");
        return new SmtpCaseMailSender(transport, "sender@example.test");
    }

    private Mail mail() {
        return new Mail(UUID.randomUUID(), UUID.randomUUID(), "recipient@example.test", "case-reference",
                "Ihr Anliegen wurde gespeichert.", "https://example.test/mieter/fall/zugang/test-only-token");
    }
}
