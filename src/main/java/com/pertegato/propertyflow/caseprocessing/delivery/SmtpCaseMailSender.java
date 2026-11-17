package com.pertegato.propertyflow.caseprocessing.delivery;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class SmtpCaseMailSender implements CaseMailSender {

    private static final String SUBJECT = "PropertyFlow: Information zu Ihrem Anliegen";
    private static final String BODY = """
            %s

            Fall: %s
            Ihre Fallansicht: %s

            Dieser Link ermöglicht Zugriff auf Ihren Fall. Bitte behandeln Sie ihn vertraulich.
            """;
    private final JavaMailSender sender;
    private final String from;

    public SmtpCaseMailSender(JavaMailSender sender, @Value("${propertyflow.case-mail.from:propertyflow@example.test}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public SendResult send(Mail mail) {
        try {
            MimeMessage message = new DeliveryMessage(sender.createMimeMessage(), mail.orderId().toString());
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(mail.recipient());
            helper.setSubject(SUBJECT);
            helper.setText(BODY.formatted(mail.information(), mail.caseId(), mail.personalUrl()));
            sender.send(message);
            return SendResult.ACCEPTED;
        } catch (MailAuthenticationException exception) {
            return SendResult.REJECTED;
        } catch (Exception exception) {
            Throwable current = exception;
            if (exception instanceof MailSendException failed && failed.getMessageExceptions().length == 1) {
                current = failed.getMessageExceptions()[0];
            }
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (current instanceof ConnectException) {
                    return SendResult.RETRYABLE;
                }
                if (current instanceof SMTPAddressFailedException failed) {
                    return failed.getReturnCode() >= 500 ? SendResult.REJECTED : SendResult.RETRYABLE;
                }
                if (current instanceof SMTPSendFailedException failed) {
                    if (failed.getValidSentAddresses() != null && failed.getValidSentAddresses().length > 0) {
                        return SendResult.UNKNOWN;
                    }
                    return failed.getReturnCode() >= 500 ? SendResult.REJECTED : SendResult.RETRYABLE;
                }
                current = current instanceof MessagingException messaging && messaging.getNextException() != null
                        ? messaging.getNextException() : current.getCause();
            }
            // SMTP offers no general idempotency guarantee after an ambiguous hand-off.
            return SendResult.UNKNOWN;
        }
    }

    private static final class DeliveryMessage extends MimeMessage {

        private final String deliveryId;

        private DeliveryMessage(MimeMessage source, String deliveryId) throws MessagingException {
            super(source.getSession());
            this.deliveryId = deliveryId;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", "<" + deliveryId + "@propertyflow>");
        }
    }
}
