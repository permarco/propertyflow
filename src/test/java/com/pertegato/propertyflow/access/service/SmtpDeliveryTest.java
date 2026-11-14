package com.pertegato.propertyflow.access.service;

import com.pertegato.propertyflow.access.demo.DemoAccounts;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "spring.mail.properties.mail.smtp.connectiontimeout=500",
        "spring.mail.properties.mail.smtp.timeout=500",
        "spring.mail.properties.mail.smtp.writetimeout=500",
        "propertyflow.staff.mail.from=sender@example.test"
})
@ActiveProfiles("frontend-demo")
@Import(SmtpDeliveryTest.LoginConfiguration.class)
@Timeout(15)
class SmtpDeliveryTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JavaMailSenderImpl sender;
    @Autowired
    StaffCodeDelivery delivery;
    @Autowired
    DemoAccounts accounts;
    @Autowired
    MutableClock clock;
    @Autowired
    AttemptedCode attempted;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        attempted.value = null;
    }

    @Test
    void realSmtpTransportDeliversUtf8AlternativePartsAndCorrectEnvelope() throws Exception {
        try (SmtpPeer peer = new SmtpPeer(Mode.ACCEPT)) {
            sender.setPort(peer.port());
            delivery.send("recipient@example.test", "042817");
            MimeMessage message = peer.message();
            assertEquals("sender@example.test", ((InternetAddress) message.getFrom()[0]).getAddress());
            assertEquals(1, message.getAllRecipients().length);
            assertEquals("recipient@example.test", ((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress());
            assertEquals("Ihr Einmalcode für PropertyFlow", message.getSubject());
            assertTrue(message.isMimeType("multipart/alternative"));
            Multipart parts = (Multipart) message.getContent();
            assertEquals(2, parts.getCount());
            assertTrue(parts.getBodyPart(0).isMimeType("text/plain"));
            assertTrue(parts.getBodyPart(1).isMimeType("text/html"));
            for (int i = 0; i < parts.getCount(); i++) {
                assertTrue("UTF-8".equalsIgnoreCase(new ContentType(parts.getBodyPart(i).getContentType()).getParameter("charset")));
                String body = (String) parts.getBodyPart(i).getContent();
                assertTrue(body.contains("042817"));
                assertTrue(body.contains("können Sie diese E-Mail ignorieren"));
                assertFalse(body.contains("${code}"));
            }
            assertTrue(peer.commands.contains("MAIL FROM:<sender@example.test>"));
            assertTrue(peer.commands.contains("RCPT TO:<recipient@example.test>"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"REFUSED", "TIMEOUT", "REJECT"})
    void actualTransportFailuresReachTheAdapterCaller(Mode mode) throws Exception {
        try (SmtpPeer peer = new SmtpPeer(mode)) {
            sender.setPort(peer.port());
            MailSendException failure = assertThrows(MailSendException.class,
                    () -> delivery.send("recipient@example.test", "042817"));
            if (mode == Mode.REFUSED) {
                assertTrue(hasCause(failure, ConnectException.class));
            } else if (mode == Mode.TIMEOUT) {
                assertTrue(hasCause(failure, SocketTimeoutException.class));
            } else {
                assertTrue(failure.getMessage().contains("550"));
                assertNotNull(peer.message());
            }
        }
    }

    static Stream<Arguments> recoveryScenarios() {
        return Stream.of(Mode.REFUSED, Mode.TIMEOUT, Mode.REJECT)
                .flatMap(mode -> Stream.of("/anmelden", "/registrieren").map(path -> Arguments.of(mode, path)));
    }

    @ParameterizedTest
    @MethodSource("recoveryScenarios")
    void failedSmtpRevokesCodeAndNewRequestRecoversThroughRealMvc(Mode mode, String path) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        boolean registration = path.equals("/registrieren");
        if (!registration) {
            accounts.activateVerifiedTenant(email);
        }
        MockHttpSession session = (MockHttpSession) mvc.perform(get(path))
                .andReturn().getRequest().getSession(false);
        String csrf = session.getAttribute("csrf").toString();
        try (SmtpPeer failed = new SmtpPeer(mode)) {
            sender.setPort(failed.port());
            mvc.perform(post(path + "/code")
                    .session(session)
                    .param("csrf", csrf)
                    .param("email", email))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"login-code\"")))
                    .andExpect(content().string(not(containsString("550"))))
                    .andExpect(content().string(not(containsString("127.0.0.1"))));
            String failedCode = attempted.value;
            assertNotNull(failedCode);
            mvc.perform(post(path + "/pruefen")
                    .session(session)
                    .param("csrf", csrf)
                    .param("code", failedCode))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/mieter/faelle")
                    .session(session))
                    .andExpect(status().isUnauthorized());
            assertEquals(!registration, accounts.findActiveByEmail(email).isPresent());
            mvc.perform(post(path + "/code")
                    .session(session)
                    .param("csrf", csrf))
                    .andExpect(status().isTooManyRequests());
        }

        clock.advance(Duration.ofMinutes(1));
        try (SmtpPeer recovered = new SmtpPeer(Mode.ACCEPT)) {
            sender.setPort(recovered.port());
            mvc.perform(post(path + "/code")
                    .session(session)
                    .param("csrf", csrf))
                    .andExpect(status().isOk());
            Multipart parts = (Multipart) recovered.message().getContent();
            String text = (String) parts.getBodyPart(0).getContent();
            Matcher code = Pattern.compile("\\b[0-9]{6}\\b").matcher(text);
            assertTrue(code.find());
            mvc.perform(post(path + "/pruefen")
                    .session(session)
                    .param("csrf", csrf)
                    .param("code", code.group()))
                    .andExpect(status().isSeeOther());
            mvc.perform(get("/mieter/faelle")
                    .session(session))
                    .andExpect(status().isOk());
            assertTrue(accounts.findActiveByEmail(email).orElseThrow().tenant());
            assertEquals(1, recovered.commands.stream().filter("DATA"::equals).count());
        }
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LoginConfiguration {

        @Bean
        MutableClock smtpTestClock() {
            return new MutableClock();
        }

        @Bean
        AttemptedCode smtpAttemptedCode() {
            return new AttemptedCode();
        }

        @Bean
        @Primary
        StaffLogin smtpTestLogin(DemoAccounts accounts, StaffCodeDelivery delivery, MutableClock clock, AttemptedCode attempted) {
            // Observe the generated code, but send through the production adapter and a real TCP connection.
            return new StaffLogin(accounts, (email, code) -> {
                attempted.value = code;
                delivery.send(email, code);
            }, clock, Runnable::run);
        }
    }

    static class AttemptedCode {
        private String value;
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    enum Mode {
        ACCEPT,
        REFUSED,
        TIMEOUT,
        REJECT
    }

    // One bounded SMTP exchange per test. It never listens outside loopback and needs no external mail service.
    static class SmtpPeer implements AutoCloseable {
        private final ServerSocket listener;
        private final Socket reservation;
        private final Thread worker;
        private final CountDownLatch release = new CountDownLatch(1);
        private final CompletableFuture<MimeMessage> received = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private final List<String> commands = new ArrayList<>();

        SmtpPeer(Mode mode) throws Exception {
            if (mode == Mode.REFUSED) {
                // Reserve a port without listening, avoiding a race with another process after closing a listener.
                reservation = new Socket();
                reservation.bind(new InetSocketAddress("127.0.0.1", 0));
                listener = null;
                worker = null;
                return;
            }
            reservation = null;
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            listener.setSoTimeout(5000);
            worker = Thread.ofPlatform().daemon().start(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    if (mode == Mode.TIMEOUT) {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } else {
                        exchange(socket, mode);
                    }
                    finished.complete(null);
                } catch (Throwable error) {
                    received.completeExceptionally(error);
                    finished.completeExceptionally(error);
                }
            });
        }

        int port() {
            return listener == null ? reservation.getLocalPort() : listener.getLocalPort();
        }

        MimeMessage message() throws Exception {
            return received.get(5, TimeUnit.SECONDS);
        }

        private void exchange(Socket socket, Mode mode) throws Exception {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            reply(output, "220 localhost test SMTP");
            String line;
            while ((line = input.readLine()) != null) {
                commands.add(line);
                if (line.startsWith("EHLO ") || line.startsWith("HELO ") || line.startsWith("MAIL FROM:")
                        || line.startsWith("RCPT TO:") || line.equals("RSET")) {
                    reply(output, "250 OK");
                } else if (line.equals("DATA")) {
                    reply(output, "354 End with dot");
                    StringBuilder data = new StringBuilder();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    }
                    received.complete(new MimeMessage(Session.getInstance(new Properties()),
                            new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.US_ASCII))));
                    reply(output, mode == Mode.REJECT ? "550 Test delivery rejected" : "250 Accepted");
                } else if (line.equals("QUIT")) {
                    reply(output, "221 Bye");
                    return;
                } else {
                    throw new AssertionError("Unexpected SMTP command: " + line);
                }
            }
        }

        private void reply(BufferedWriter output, String line) throws Exception {
            output.write(line + "\r\n");
            output.flush();
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            if (reservation != null) {
                reservation.close();
            }
            if (listener != null) {
                listener.close();
                worker.join(6000);
                assertFalse(worker.isAlive(), "SMTP worker must stop");
                finished.get(1, TimeUnit.SECONDS);
            }
        }
    }
}
