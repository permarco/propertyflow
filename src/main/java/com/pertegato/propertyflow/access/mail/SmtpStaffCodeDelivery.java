package com.pertegato.propertyflow.access.mail;

import com.pertegato.propertyflow.access.service.StaffCodeDelivery;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
@Profile("frontend-demo")
class SmtpStaffCodeDelivery implements StaffCodeDelivery {
    private static final String SUBJECT = "Ihr Einmalcode für PropertyFlow";
    private static final String ENCODING = StandardCharsets.UTF_8.name();
    private static final String PLAIN_TEXT = """
            Ihr Einmalcode für PropertyFlow lautet: %s

            Geben Sie den Code auf der PropertyFlow-Anmeldeseite ein. Er ist einmal verwendbar.
            Wenn Sie keinen Code angefordert haben, können Sie diese E-Mail ignorieren.
            """;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String from;

    SmtpStaffCodeDelivery(JavaMailSender mailSender, TemplateEngine templateEngine, @Value("${propertyflow.staff.mail.from}") String from) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
    }

    @Override
    public void send(String email, String code) {
        Context context = new Context(Locale.forLanguageTag("de-CH"));
        context.setVariable("code", code);
        String html = templateEngine.process("access/staff-code-email", context);

        mailSender.send(message -> {
            MimeMessageHelper headers = new MimeMessageHelper(message, ENCODING);
            headers.setFrom(from);
            headers.setTo(email);
            headers.setSubject(SUBJECT);

            MimeBodyPart plainPart = new MimeBodyPart();
            plainPart.setText(PLAIN_TEXT.formatted(code), ENCODING);
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setText(html, ENCODING, "html");

            MimeMultipart body = new MimeMultipart("alternative");
            body.addBodyPart(plainPart);
            body.addBodyPart(htmlPart);
            message.setContent(body);
        });
    }
}
