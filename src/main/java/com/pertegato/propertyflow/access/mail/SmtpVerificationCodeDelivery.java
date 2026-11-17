package com.pertegato.propertyflow.access.mail;

import com.pertegato.propertyflow.access.contract.VerificationCodeDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("propertyflow-backend & !frontend-demo")
public class SmtpVerificationCodeDelivery implements VerificationCodeDelivery {
    private final JavaMailSender sender;
    private final String from;

    public SmtpVerificationCodeDelivery(JavaMailSender sender, @Value("${propertyflow.staff.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("PropertyFlow: Kontaktadresse bestätigen");
        message.setText("""
                Die Verwaltung hat nach einer Identitätsprüfung einen Ersatz Ihres Fallzugangs angefordert.
                Ihr Bestätigungscode lautet: %s

                Dieser Code gilt 15 Minuten ausschliesslich für diesen Verwaltungsvorgang, nicht zur Anmeldung.
                Teilen Sie ihn nur der von Ihnen kontaktierten Verwaltung für diesen Vorgang mit.
                Ohne bestätigten Code bleibt Ihr bisheriger Fallzugang unverändert.
                """.formatted(code));
        sender.send(message);
    }
}
