package com.pertegato.propertyflow.caseprocessing.controller;

import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.access.web.BrowserResponses;
import com.pertegato.propertyflow.access.web.StaffWebSession;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatusView;
import com.pertegato.propertyflow.caseprocessing.contract.PublicMessage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseSummary;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

@Controller
@Profile("propertyflow-backend & !frontend-demo")
@RequestMapping(StaffWebSession.STAFF_LIST)
@RequiredArgsConstructor
public class StaffCaseController {

    private static final Logger LOG = LoggerFactory.getLogger(StaffCaseController.class);
    private static final String TECHNICAL_ERROR = "Die Falldaten sind vorübergehend nicht verfügbar. Bitte versuchen Sie es später erneut.";
    private static final List<String> URGENCY_OPTIONS = List.of(PersonalCaseController.NOT_ASSESSED, "Kritisch", "Hoch", "Normal", "Niedrig");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final StaffCaseListing listing;
    private final AccountCaseReading reading;

    @ModelAttribute
    void protect(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String urgency,
            @RequestParam(defaultValue = "false") String closed, @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "false") String desc, @RequestParam(defaultValue = "1") String page,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        return read(request, response, model, context -> {
            StaffCaseQuery query = new StaffCaseQuery(q, urgency, flag(closed), sort, flag(desc), pageNumber(page));
            StaffCasePage result = listing.listForStaff(context, query);
            model.addAttribute("query", q);
            model.addAttribute("urgencyFilter", urgency);
            model.addAttribute("urgencyOptions", URGENCY_OPTIONS);
            model.addAttribute("includeClosed", query.includeClosed());
            model.addAttribute("sort", sort);
            model.addAttribute("descending", query.descending());
            model.addAttribute("page", result.page());
            model.addAttribute("totalPages", result.totalPages());
            model.addAttribute("total", result.total());
            model.addAttribute("rows", result.rows().stream().map(StaffCaseController::row).toList());
            model.addAttribute("listUrl", listUrl(query, result.page()));
            return "staff/list";
        });
    }

    @GetMapping("/{id}")
    String detail(@PathVariable String id, @RequestParam(required = false) String returnTo,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        return read(request, response, model, context -> {
            StaffCaseView view = reading.readForStaff(context, id);
            model.addAttribute("recommendation", view.recommendation().orElse(null));
            TenantCaseView publicView = view.publicView();
            CaseStatusView state = publicView.state();
            model.addAttribute("caseId", state.caseId());
            model.addAttribute("memoActor", context.accountId());
            model.addAttribute("title", publicView.title());
            model.addAttribute("originalUnit", publicView.originalUnit());
            model.addAttribute("assignedUnit", publicView.assignedUnit().orElse(null));
            model.addAttribute("contactEmail", view.contactEmail());
            model.addAttribute("status", PersonalCaseController.statusLabel(state.status()));
            model.addAttribute("urgency", urgencyLabel(state));
            model.addAttribute("urgencyValue", state.urgency().map(Enum::name).orElse(""));
            model.addAttribute("urgencyCss", urgencyCss(state));
            model.addAttribute("urgencySource", publicView.urgencySource()
                    .map(source -> switch (source) {
                        case STAFF -> "Immobilienverwaltung";
                        case SYSTEM -> "System";
                    }).orElse(""));
            model.addAttribute("version", publicView.version());
            model.addAttribute("received", format(publicView.receivedAt()));
            model.addAttribute("lastContact", format(publicView.lastTenantContactAt()));
            model.addAttribute("followUp", view.followUpDate().map(DATE::format).orElse(""));
            model.addAttribute("reviewReasons", view.reviewReasons());
            model.addAttribute("returnTo", StaffWebSession.safeListTarget(returnTo));
            model.addAttribute("history", history(view));
            return "staff/detail";
        });
    }

    private String read(HttpServletRequest request, HttpServletResponse response, Model model,
            Function<WebAccountContext, String> operation) {
        HttpSession session = StaffWebSession.cookieSession(request);
        SignedIn signedIn = signedIn(session);
        if (signedIn == null) {
            return loginRequired(request, response, model);
        }
        try {
            model.addAttribute("csrf", signedIn.csrf());
            return operation.apply(new WebAccountContext(signedIn.account()));
        } catch (CaseServiceException exception) {
            return switch (exception.code()) {
                case UNAUTHENTICATED -> {
                    clearStaleAccount(session, signedIn);
                    yield loginRequired(request, response, model);
                }
                case FORBIDDEN -> denied(request, response, model);
                case CASE_NOT_AVAILABLE -> error(response, model, HttpStatus.NOT_FOUND, "Dieser Fall ist nicht verfügbar.");
                case INVALID_INPUT -> error(response, model, HttpStatus.BAD_REQUEST, "Prüfen Sie die Such- und Filterangaben.");
                case STORAGE_UNAVAILABLE -> error(response, model, HttpStatus.SERVICE_UNAVAILABLE, TECHNICAL_ERROR);
                default -> error(response, model, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR);
            };
        } catch (RuntimeException exception) {
            LOG.error("Staff case read failed ({})", exception.getClass().getSimpleName());
            return error(response, model, HttpStatus.INTERNAL_SERVER_ERROR, TECHNICAL_ERROR);
        }
    }

    private static SignedIn signedIn(HttpSession session) {
        if (session == null) {
            return null;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            try {
                return session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID account
                        ? new SignedIn(account, (String) session.getAttribute(StaffWebSession.CSRF)) : null;
            } catch (IllegalStateException expired) {
                return null;
            }
        }
    }

    private static void clearStaleAccount(HttpSession session, SignedIn stale) {
        synchronized (WebUtils.getSessionMutex(session)) {
            if (Objects.equals(stale, signedIn(session))) {
                session.removeAttribute(StaffWebSession.ACCOUNT);
            }
        }
    }

    private static String denied(HttpServletRequest request, HttpServletResponse response, Model model) {
        model.asMap().clear();
        response.setHeader("X-PropertyFlow-Access", "denied");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        if (!background(request)) {
            response.setContentType("text/plain;charset=UTF-8");
            try {
                response.getWriter().write("Sie haben keinen Zugriff auf den Mitarbeiterbereich.");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return null;
    }

    private static String loginRequired(HttpServletRequest request, HttpServletResponse response, Model model) {
        model.asMap().clear();
        if (background(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        String target = StaffWebSession.safeTarget(query == null ? path : path + "?" + query);
        request.getSession().setAttribute(StaffWebSession.RETURN_TO, target);
        BrowserResponses.seeOther(request, response, StaffWebSession.LOGIN);
        return null;
    }

    private static boolean background(HttpServletRequest request) {
        return "partial".equals(request.getHeader("X-PropertyFlow-Request"))
                || "empty".equals(request.getHeader("Sec-Fetch-Dest"));
    }

    private static String error(HttpServletResponse response, Model model, HttpStatus status, String message) {
        model.asMap().clear();
        model.addAttribute("error", message);
        response.setStatus(status.value());
        return "staff/error";
    }

    private static boolean flag(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new CaseServiceException(CaseErrorCode.INVALID_INPUT, Map.of());
        };
    }

    private static int pageNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new CaseServiceException(CaseErrorCode.INVALID_INPUT, Map.of());
        }
    }

    private static String listUrl(StaffCaseQuery query, int page) {
        return UriComponentsBuilder.fromPath(StaffWebSession.STAFF_LIST)
                .queryParam("q", "{q}")
                .queryParam("urgency", "{urgency}")
                .queryParam("closed", query.includeClosed())
                .queryParam("sort", "{sort}")
                .queryParam("desc", query.descending())
                .queryParam("page", page)
                .encode()
                .buildAndExpand(query.query(), query.urgency(), query.sort())
                .toUriString();
    }

    private static CaseRow row(StaffCaseSummary row) {
        return new CaseRow(row.state().caseId(), row.title(), row.unit(), format(row.receivedAt()), format(row.lastTenantContactAt()),
                row.followUp().map(DATE::format).orElse(""), urgencyLabel(row.state()), urgencyCss(row.state()),
                PersonalCaseController.statusLabel(row.state().status()));
    }

    private static List<HistoryEntry> history(StaffCaseView view) {
        List<Event> events = new ArrayList<>();
        view.publicView().messages().forEach(message -> {
            String role = switch (message.author()) {
                case TENANT -> "Mieter";
                case STAFF -> "Immobilienverwaltung";
                case SYSTEM -> "System";
            };
            events.add(new Event(role, message.text(), message.occurredAt(), false, PersonalCaseController.answerLabel(message),
                    message.answerState() == PublicMessage.AnswerState.OPEN ? message.id() : null));
        });
        view.internalMemos().forEach(memo -> events.add(new Event("Immobilienverwaltung", memo.text(), memo.occurredAt(), true)));
        view.internalChanges().forEach(change -> events.add(new Event("Immobilienverwaltung · " + change.actorName(),
                change.description(), change.occurredAt(), true)));
        view.publicView().urgencyHistory().forEach(change -> {
            if (view.internalChanges().stream().anyMatch(internal -> internal.id().equals(change.id()))) {
                return;
            }
            String previous = change.previous().map(PersonalCaseController::urgencyLabel).orElse(PersonalCaseController.NOT_ASSESSED);
            String text = "Dringlichkeit: " + previous + " → " + PersonalCaseController.urgencyLabel(change.current());
            String role = switch (change.source()) {
                case STAFF -> "Immobilienverwaltung";
                case SYSTEM -> "System";
            };
            events.add(new Event(role, text, change.occurredAt(), false));
        });
        return events.stream()
                .sorted(Comparator.comparing(Event::at))
                .map(event -> new HistoryEntry(event.role(), event.text(), format(event.at()), event.internal(), event.answerLabel(), event.clearAnswerId()))
                .toList();
    }

    private static String urgencyLabel(CaseStatusView state) {
        return state.urgency().map(PersonalCaseController::urgencyLabel).orElse(PersonalCaseController.NOT_ASSESSED);
    }

    private static String urgencyCss(CaseStatusView state) {
        return state.urgency().map(PersonalCaseController::urgencyCss).orElse("urgency-0");
    }

    private static String format(Instant time) {
        return PersonalCaseController.LOCAL_TIME.format(time);
    }

    public record CaseRow(String caseId, String title, String unit, String received, String lastContact, String followUp,
            String urgency, String urgencyCss, String status) {
    }

    public record HistoryEntry(String role, String text, String timestamp, boolean internal, String answerLabel, String clearAnswerId) {
    }

    private record Event(String role, String text, Instant at, boolean internal, String answerLabel, String clearAnswerId) {
        private Event(String role, String text, Instant at, boolean internal) {
            this(role, text, at, internal, "", null);
        }
    }

    private record SignedIn(UUID account, String csrf) {
    }
}
