package com.pertegato.propertyflow.caseprocessing.demo;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/**
 * Disposable, process-local fixtures for the frontend demo only.
 * This is deliberately not a persistence or workflow implementation.
 */
@Service
@Profile("frontend-demo")
public class DemoCases {
    public static final List<String> URGENCIES = List.of("Noch nicht bewertet", "Kritisch", "Hoch", "Normal", "Niedrig");
    public static final List<String> STATUSES = List.of("Eingegangen", "In Abklärung", "Wartet auf Mieterantwort",
            "Mitarbeiterprüfung erforderlich", "In Bearbeitung", "Wartet auf externe Rückmeldung", "Wartet auf Wiedervorlage");
    public static final List<String> UNITS = List.of("Seestrasse 24 · 3. OG links", "Seestrasse 24 · 2. OG rechts",
            "Gartenstrasse 8 · Wohnung 1", "Bahnhofstrasse 12 · Wohnung 4");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.of("Europe/Zurich"));
    private final Map<String, CaseData> cases = new LinkedHashMap<>();
    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, String> submissions = new HashMap<>();

    public record Entry(String id, String role, String text, Instant at, boolean internal, boolean event, boolean answerRequired, String reason) {
        public String time() { return TIME.format(at); }
    }
    public record View(String id, String title, String description, String originalUnit, String unit, String email,
                       Instant received, Instant contact, String urgency, String source, String status, LocalDate followUp,
                       String review, long version, List<Entry> entries) {
        public boolean closed() { return status.equals("Abgeschlossen"); }
        public String receivedText() { return TIME.format(received); }
        public String contactText() { return TIME.format(contact); }
        public String urgencyClass() { return "urgency-" + URGENCIES.indexOf(urgency); }
        public String followUpText() { return followUp == null ? "Keine Wiedervorlage" : followUp.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
        public String maskedEmail() { return email.charAt(0) + "***" + email.substring(email.indexOf('@')); }
    }
    public record Page(List<View> items, int total, int page, int pages) {}
    private static class CaseData {
        String id, title, description, originalUnit, unit, email, urgency, source = "System", status, review = "";
        Instant received, contact;
        LocalDate followUp;
        long version = 1;
        List<Entry> entries = new ArrayList<>();
        Set<String> operations = new HashSet<>();
        View view(boolean tenant) {
            return new View(id, title, description, originalUnit, unit, email, received, contact, urgency, source, status,
                    followUp, tenant ? "" : review, version, entries.stream().filter(e -> !tenant || !e.internal())
                    .map(e -> tenant ? new Entry(e.id(), e.role(), e.text(), e.at(), false, e.event(), e.answerRequired(), "") : e).toList());
        }
    }
    public DemoCases() {
        String[] titles = {"Heizung funktioniert nicht", "Wasser unter dem Waschbecken", "Haustür schliesst nicht", "Licht im Treppenhaus", "Frage zur Waschküche", "Rollladen klemmt"};
        IntStream.rangeClosed(1, 56).forEach(i -> {
            CaseData c = new CaseData();
            c.id = "REQ-2026-%03d".formatted(i);
            c.title = titles[(i - 1) % titles.length];
            c.description = i == 1 ? "Seit gestern sind alle Heizkörper kalt. Die Raumtemperatur liegt bei 16 °C." : "Guten Tag. " + c.title + ". Bitte prüfen Sie das Anliegen.";
            c.originalUnit = i == 1 ? "Seestrasse 24, Wohnung 3.2" : UNITS.get(i % UNITS.size());
            c.unit = i == 1 ? UNITS.getFirst() : "";
            c.email = "mieter@example.ch";
            c.received = Instant.parse("2026-10-10T07:12:00Z").minusSeconds(i * 3600L);
            c.contact = c.received;
            c.urgency = i == 1 ? "Hoch" : URGENCIES.get(i % URGENCIES.size());
            c.status = i % 9 == 0 ? "Abgeschlossen" : STATUSES.get((i - 1) % 6);
            if (i == 1) { c.status = STATUSES.get(3); c.review = "Weiteres Vorgehen bei Heizungsausfall prüfen."; }
            if (i % 7 == 0 && !c.status.equals("Abgeschlossen")) { c.followUp = LocalDate.of(2026, 10, 15); c.status = STATUSES.get(6); }
            c.entries.add(entry("Mieter", c.description, c.received, false, false, false, ""));
            c.entries.add(entry("System", "Das Anliegen wurde erfasst und zur Prüfung weitergeleitet.", c.received.plusSeconds(60), false, false, false, ""));
            if (i == 1) {
                c.entries.add(entry("System", "Dringlichkeit: Noch nicht bewertet → Hoch", c.received.plusSeconds(60), false, true, false, ""));
                c.entries.add(entry("Immobilienverwaltung", "Hauswart telefonisch nicht erreicht. Weiteres Vorgehen abklären.", c.received.plusSeconds(780), true, false, false, ""));
            }
            cases.put(c.id, c);
        });
        tokens.put("demo-aktiver-fall", "REQ-2026-001");
        tokens.put("demo-abgeschlossener-fall", "REQ-2026-009");
    }
    private static Entry entry(String role, String text, Instant at, boolean internal, boolean event, boolean answer, String reason) {
        return new Entry(UUID.randomUUID().toString(), role, text, at, internal, event, answer, reason);
    }
    private CaseData data(String id) {
        CaseData c = cases.get(id);
        if (c == null) throw new ResponseStatusException(NOT_FOUND, "Der Fall ist nicht verfügbar.");
        return c;
    }
    public synchronized View get(String id) { return data(id).view(false); }
    public synchronized View tenant(String token) {
        String id = tokens.get(token);
        if (id == null) throw new ResponseStatusException(NOT_FOUND, "Dieser Fall-Link ist nicht verfügbar.");
        return data(id).view(true);
    }
    private static String normalize(String value) {
        return value.toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L}\\p{N}\\s]", "");
    }
    public synchronized Page list(String query, String urgency, boolean closed, String sort, boolean descending, int requestedPage) {
        String[] words = normalize(query).strip().split("\\s+");
        List<View> result = cases.values().stream().map(c -> c.view(false))
                .filter(c -> closed || !c.closed()).filter(c -> urgency.isEmpty() || c.urgency().equals(urgency))
                .filter(c -> {
                    List<String> fields = new ArrayList<>(List.of(c.id(), c.title(), c.description(), c.originalUnit(), c.unit()));
                    c.entries().stream().filter(e -> !e.event()).forEach(e -> fields.add(e.text()));
                    return Arrays.stream(words).allMatch(w -> fields.stream().anyMatch(f -> normalize(f).contains(w)));
                }).sorted(comparator(sort, descending)).toList();
        int pages = Math.max(1, (result.size() + 49) / 50);
        int page = Math.max(1, Math.min(pages, requestedPage));
        return new Page(result.subList((page - 1) * 50, Math.min(page * 50, result.size())), result.size(), page, pages);
    }
    private Comparator<View> comparator(String sort, boolean descending) {
        Comparator<View> cmp = switch (sort) {
            case "title" -> Comparator.comparing(View::title, String.CASE_INSENSITIVE_ORDER);
            case "unit" -> Comparator.comparing(c -> c.unit().isBlank() ? c.originalUnit() : c.unit(), String.CASE_INSENSITIVE_ORDER);
            case "received" -> Comparator.comparing(View::received);
            case "contact" -> Comparator.comparing(View::contact);
            case "urgency" -> Comparator.comparingInt(c -> URGENCIES.indexOf(c.urgency()));
            case "status" -> Comparator.comparing(View::status);
            case "followUp" -> Comparator.comparing(View::followUp, Comparator.nullsLast(descending ? Comparator.<LocalDate>reverseOrder() : Comparator.<LocalDate>naturalOrder()));
            default -> Comparator.<View>comparingInt(c -> URGENCIES.indexOf(c.urgency())).thenComparing(View::contact);
        };
        if (descending && !sort.equals("followUp") && !sort.equals("default")) cmp = cmp.reversed();
        return cmp.thenComparing(View::id);
    }
    public static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new ResponseStatusException(BAD_REQUEST, "Bitte Pflichtfelder ausfüllen und die maximale Länge beachten.");
        return value.strip();
    }
    public synchronized String create(String title, String unit, String email, String description, String operation) {
        required(operation, 80);
        if (submissions.containsKey(operation)) return submissions.get(operation);
        title = required(title, 150); unit = required(unit, 200); email = required(email, 254); description = required(description, 5000);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new ResponseStatusException(BAD_REQUEST, "Bitte eine gültige E-Mail-Adresse eingeben.");
        CaseData c = new CaseData();
        c.id = "REQ-2026-%03d".formatted(cases.size() + 1); c.title = title; c.originalUnit = unit; c.unit = "";
        c.email = email; c.description = description; c.received = Instant.now(); c.contact = c.received;
        c.status = "Eingegangen"; c.urgency = URGENCIES.getFirst();
        c.entries.add(entry("Mieter", description, c.received, false, false, false, ""));
        cases.put(c.id, c);
        String token = UUID.randomUUID().toString();
        tokens.put(token, c.id); submissions.put(operation, token);
        return token;
    }
    public synchronized void message(String id, String token, String text, boolean internal, boolean answer, String operation) {
        CaseData c = data(token == null ? id : tenant(token).id());
        required(operation, 80);
        operation = "message:" + operation;
        if (c.operations.contains(operation)) return;
        active(c);
        text = required(text, 5000);
        boolean tenant = token != null;
        if (tenant && (internal || answer)) throw new ResponseStatusException(BAD_REQUEST);
        c.entries.add(entry(tenant ? "Mieter" : "Immobilienverwaltung", text, Instant.now(), internal, false, !internal && answer, ""));
        if (tenant) c.contact = Instant.now();
        if (answer && !internal && c.review.isBlank() && c.followUp == null) c.status = "Wartet auf Mieterantwort";
        c.operations.add(operation); c.version++;
    }
    private void active(CaseData c) {
        if (c.status.equals("Abgeschlossen")) throw new ResponseStatusException(CONFLICT, "Der Fall ist abgeschlossen. Neue Nachrichten und Änderungen sind gesperrt.");
    }
    public synchronized void change(String id, String action, String value, String reason, long version, String operation) {
        CaseData c = data(id);
        required(operation, 80);
        operation = action + ":" + operation;
        if (c.operations.contains(operation)) return;
        if (c.version != version) throw new ResponseStatusException(CONFLICT, "Konnte nicht erfolgreich gespeichert werden. Der Fall wurde inzwischen geändert.");
        if (reason.length() > 1000) throw new ResponseStatusException(BAD_REQUEST, "Die Begründung ist zu lang.");
        if (!action.equals("reopen")) active(c);
        String event;
        boolean internal = false;
        switch (action) {
            case "urgency" -> {
                if (!URGENCIES.subList(1, 5).contains(value)) throw new ResponseStatusException(BAD_REQUEST);
                event = "Dringlichkeit: " + c.urgency + " → " + value;
                c.urgency = value; c.source = "Immobilienverwaltung";
            }
            case "status" -> {
                if (!STATUSES.contains(value) || (value.equals(STATUSES.get(6)) && c.followUp == null)
                        || (value.equals(STATUSES.get(2)) && c.entries.stream().noneMatch(Entry::answerRequired)))
                    throw new ResponseStatusException(BAD_REQUEST, "Dieser Status benötigt eine Wiedervorlage bzw. offene Rückfrage.");
                event = "Status: " + c.status + " → " + value; c.status = value;
                if (value.equals("In Bearbeitung")) c.review = "";
            }
            case "followUp" -> {
                LocalDate date;
                try { date = value.startsWith("days:") ? LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(Integer.parseInt(value.substring(5))) : LocalDate.parse(value); }
                catch (RuntimeException e) { throw new ResponseStatusException(BAD_REQUEST, "Bitte einen gültigen zukünftigen Termin wählen."); }
                if (!date.isAfter(LocalDate.now(ZoneId.of("Europe/Zurich")))) throw new ResponseStatusException(BAD_REQUEST, "Der Termin muss in der Zukunft liegen.");
                event = "Wiedervorlage: " + c.followUp + " → " + date; c.followUp = date; c.status = STATUSES.get(6); internal = true;
            }
            case "clearFollowUp" -> { event = "Wiedervorlage gelöscht"; c.followUp = null; c.status = "In Bearbeitung"; internal = true; }
            case "unit" -> {
                String assignment = required(value, 200);
                event = "Zuordnung: " + c.unit + " → " + assignment; c.unit = assignment; internal = true;
            }
            case "close" -> {
                event = "Status: " + c.status + " → Abgeschlossen"; c.status = "Abgeschlossen"; c.followUp = null;
                reason = "Vom Mitarbeiter geprüft: keine weitere Bearbeitung erforderlich.";
                c.entries.replaceAll(e -> new Entry(e.id(), e.role(), e.text(), e.at(), e.internal(), e.event(), false, e.reason()));
            }
            case "reopen" -> {
                if (!c.status.equals("Abgeschlossen")) throw new ResponseStatusException(CONFLICT);
                c.status = c.review.isBlank() ? "In Bearbeitung" : STATUSES.get(3);
                event = "Status: Abgeschlossen → " + c.status;
            }
            case "clearAnswer" -> {
                if (c.entries.stream().noneMatch(e -> e.id().equals(value) && e.answerRequired())) throw new ResponseStatusException(CONFLICT);
                c.entries.replaceAll(e -> e.id().equals(value) ? new Entry(e.id(), e.role(), e.text(), e.at(), e.internal(), e.event(), false, e.reason()) : e);
                if (c.entries.stream().noneMatch(Entry::answerRequired) && c.review.isBlank() && c.followUp == null) c.status = "In Bearbeitung";
                event = "Antwortbedarf aufgehoben"; internal = true;
            }
            default -> throw new ResponseStatusException(BAD_REQUEST);
        }
        c.entries.add(entry("Immobilienverwaltung", event, Instant.now(), internal, true, false, reason));
        c.version++; c.operations.add(operation);
    }
}
