package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.StaffDirectory;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.InternalChange;
import com.pertegato.propertyflow.caseprocessing.contract.McpCaseReading.CaseStateFilter;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseSummary;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.Urgency;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseDataStore;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import com.pertegato.propertyflow.caseprocessing.persistence.ConversationEvaluationStore;
import com.pertegato.propertyflow.caseprocessing.persistence.InternalMemoEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.MailDeliveryStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffChangeStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.StaffReviewStore;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.TenantMessageStore;
import com.pertegato.propertyflow.caseprocessing.persistence.UrgencyStore;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend")
class StaffCaseService implements StaffCaseListing, AccountCaseReading {

    private static final int PAGE_SIZE = 50;
    private static final String NOT_ASSESSED = "Noch nicht bewertet";
    private static final Set<String> URGENCIES = Set.of("", NOT_ASSESSED, "Kritisch", "Hoch", "Normal", "Niedrig");
    private static final String ID = "id";
    private static final String LAST_CONTACT = "lastTenantContactAt";
    private static final List<String> SEARCH_FIELDS = List.of(ID, "title", "originalUnit", "assignedUnit", "description");
    private static final Pattern NON_SEARCH_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}\\s]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "default", LAST_CONTACT, "title", "title", "unit", "displayUnit", "received", "receivedAt",
            "contact", LAST_CONTACT, "followUp", "followUpDueAt", "urgency", "urgencyRank", "status", "status");

    private final StaffAccountAccess access;
    private final CaseRepository cases;
    private final MailDeliveryStore mailDelivery;
    private final CaseDataStore memos;
    private final UrgencyStore urgencies;
    private final StaffReviewStore reviews;
    private final StaffChangeStore changes;
    private final StaffDirectory directory;
    private final TenantMessageStore messages;
    private final StaffMessageStore staffMessages;
    private final ConversationEvaluationStore evaluations;
    private final TransactionTemplate reads;

    StaffCaseService(StaffAccountAccess access, CaseRepository cases, MailDeliveryStore mailDelivery, CaseDataStore memos,
            PlatformTransactionManager transactions, UrgencyStore urgencies, StaffReviewStore reviews,
            StaffChangeStore changes, StaffDirectory directory, TenantMessageStore messages, StaffMessageStore staffMessages,
            ConversationEvaluationStore evaluations) {
        this.access = access;
        this.cases = cases;
        this.mailDelivery = mailDelivery;
        this.memos = memos;
        this.urgencies = urgencies;
        this.reviews = reviews;
        this.changes = changes;
        this.directory = directory;
        this.messages = messages;
        this.staffMessages = staffMessages;
        this.evaluations = evaluations;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    @Override
    public StaffCaseView readForStaff(WebAccountContext context, String caseId) {
        return authorizedRead(context, () -> {
            CaseEntity stored = cases.findById(caseId).orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
            return new StaffCaseView(CaseProjections.tenantView(stored, urgencies.publicHistory(caseId), messages.history(caseId), staffMessages.history(caseId)), stored.getContactEmail(),
                    Optional.ofNullable(stored.getFollowUpDueAt()).map(due -> due.atZone(FollowUpDate.ZONE).toLocalDate()),
                    Stream.concat(mailDelivery.reviewReasons(caseId).stream(), reviews.reasons(caseId).stream())
                            .distinct().toList(), memos.memos(caseId), internalChanges(caseId), evaluations.recommendation(caseId, stored.getVersion()));
        });
    }

    private List<InternalChange> internalChanges(String caseId) {
        List<InternalChange> history = changes.history(caseId);
        Map<UUID, String> names = directory.displayNames(history.stream().map(InternalChange::actorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return history.stream().map(change -> new InternalChange(change.id(), change.description(), change.actorId(),
                change.actorId() == null ? change.actorName() : names.getOrDefault(change.actorId(), "Mitarbeiter"), change.occurredAt())).toList();
    }

    @Override
    public TenantCaseView readForTenant(WebAccountContext context, String caseId) {
        throw failure(CaseErrorCode.FORBIDDEN);
    }

    @Override
    public StaffCasePage listForStaff(WebAccountContext context, StaffCaseQuery query) {
        return authorizedRead(context, () -> findPage(query, filters(query)));
    }

    StaffCasePage listForMcp(WebAccountContext context, CaseStateFilter state, CaseStatus status, String urgency, int page) {
        StaffCaseQuery query = new StaffCaseQuery("", urgency, state != CaseStateFilter.ACTIVE, "default", true, page);
        Specification<CaseEntity> matching = filters(query).and((root, criteria, builder) -> {
            Predicate filter = state == CaseStateFilter.CLOSED
                    ? builder.equal(root.get("status"), CaseStatus.CLOSED)
                    : builder.conjunction();
            return status == null ? filter : builder.and(filter, builder.equal(root.get("status"), status));
        });
        return authorizedRead(context, () -> findPage(query, matching));
    }

    TenantCaseView readPublicForStaff(WebAccountContext context, String caseId) {
        return authorizedRead(context, () -> {
            CaseEntity stored = cases.findById(caseId).orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
            return CaseProjections.tenantView(stored, List.of(), messages.history(caseId), staffMessages.history(caseId));
        });
    }

    private StaffCasePage findPage(StaffCaseQuery query, Specification<CaseEntity> matching) {
        if (query.query().length() > 200 || !URGENCIES.contains(query.urgency()) || !SORT_PROPERTIES.containsKey(query.sort())) {
            throw failure(CaseErrorCode.INVALID_INPUT);
        }
        long total = cases.count(matching);
        int totalPages = (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.ceil((double) total / PAGE_SIZE)));
        int page = Math.clamp(query.page(), 1, totalPages);
        if (total == 0) {
            return new StaffCasePage(List.of(), 0, page, totalPages);
        }
        Specification<CaseEntity> ordered = matching.and(ordering(query));
        List<StaffCaseSummary> rows = cases.findBy(ordered, selection -> selection.slice(PageRequest.of(page - 1, PAGE_SIZE)))
                .map(stored -> new StaffCaseSummary(CaseProjections.state(stored), stored.getTitle(), stored.getDisplayUnit(),
                        stored.getReceivedAt(), stored.getLastTenantContactAt(),
                        Optional.ofNullable(stored.getFollowUpDueAt()).map(due -> due.atZone(FollowUpDate.ZONE).toLocalDate())))
                .getContent();
        return new StaffCasePage(rows, total, page, totalPages);
    }

    private Specification<CaseEntity> ordering(StaffCaseQuery query) {
        return (root, criteria, builder) -> {
            List<Order> orders = new ArrayList<>();
            if ("default".equals(query.sort())) {
                orders.add(builder.asc(root.get("urgencyRank")));
                orders.add(builder.asc(root.get(LAST_CONTACT)));
            } else {
                Expression<?> value = root.get(SORT_PROPERTIES.get(query.sort()));
                if ("title".equals(query.sort()) || "unit".equals(query.sort())) {
                    value = builder.lower(root.get(SORT_PROPERTIES.get(query.sort())));
                } else if ("followUp".equals(query.sort())) {
                    // Fälle ohne Termin bleiben in beiden Richtungen hinter den terminierten Fällen.
                    orders.add(builder.asc(builder.selectCase().when(builder.isNull(value), 1).otherwise(0)));
                } else if ("status".equals(query.sort())) {
                    // Alphabetische Reihenfolge der deutschen Statusbezeichnungen statt der technischen Enum-Namen.
                    value = builder.selectCase(root.get("status"))
                            .when(CaseStatus.CLOSED, 0)
                            .when(CaseStatus.RECEIVED, 1)
                            .when(CaseStatus.UNDER_CLARIFICATION, 2)
                            .when(CaseStatus.IN_PROGRESS, 3)
                            .when(CaseStatus.STAFF_REVIEW_REQUIRED, 4)
                            .when(CaseStatus.WAITING_FOR_EXTERNAL, 5)
                            .when(CaseStatus.WAITING_FOR_TENANT, 6)
                            .when(CaseStatus.WAITING_FOR_FOLLOW_UP, 7).otherwise(8);
                }
                orders.add(query.descending() ? builder.desc(value) : builder.asc(value));
            }
            orders.add(builder.asc(root.get(ID)));
            criteria.orderBy(orders);
            return builder.conjunction();
        };
    }

    private Specification<CaseEntity> filters(StaffCaseQuery query) {
        String cleaned = NON_SEARCH_CHARACTER.matcher(query.query().toLowerCase(Locale.ROOT)).replaceAll("").strip();
        List<String> terms = cleaned.isBlank() ? List.of() : List.of(WORD_BOUNDARY.split(cleaned));
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!query.includeClosed()) {
                predicates.add(builder.notEqual(root.get("status"), CaseStatus.CLOSED));
            }
            if (NOT_ASSESSED.equals(query.urgency())) {
                predicates.add(builder.isNull(root.get("urgency")));
            } else if (!query.urgency().isBlank()) {
                Urgency urgency = switch (query.urgency()) {
                    case "Kritisch" -> Urgency.CRITICAL;
                    case "Hoch" -> Urgency.HIGH;
                    case "Normal" -> Urgency.NORMAL;
                    case "Niedrig" -> Urgency.LOW;
                    default -> throw failure(CaseErrorCode.INVALID_INPUT);
                };
                predicates.add(builder.equal(root.get("urgency"), urgency));
            }
            for (String term : terms) {
                Predicate[] fields = SEARCH_FIELDS.stream()
                        .map(field -> matchesTerm(root, builder, field, term))
                        .toArray(Predicate[]::new);
                Subquery<Integer> memo = criteria.subquery(Integer.class);
                Root<InternalMemoEntity> internal = memo.from(InternalMemoEntity.class);
                Expression<String> normalized = builder.function("regexp_replace", String.class,
                        builder.lower(internal.get("text")), builder.literal("[^[:alnum:][:space:]]"), builder.literal(""), builder.literal("g"));
                memo.select(builder.literal(1)).where(builder.equal(internal.get("caseId"), root.get(ID)),
                        builder.like(normalized, "%" + term + "%"));
                Subquery<Integer> message = criteria.subquery(Integer.class);
                Root<TenantMessageEntity> external = message.from(TenantMessageEntity.class);
                Expression<String> normalizedMessage = builder.function("regexp_replace", String.class,
                        builder.lower(external.get("text")), builder.literal("[^[:alnum:][:space:]]"), builder.literal(""), builder.literal("g"));
                message.select(builder.literal(1)).where(builder.equal(external.get("caseId"), root.get(ID)),
                        builder.like(normalizedMessage, "%" + term + "%"));
                Subquery<Integer> staffMessage = criteria.subquery(Integer.class);
                Root<StaffMessageEntity> published = staffMessage.from(StaffMessageEntity.class);
                Expression<String> normalizedStaffMessage = builder.function("regexp_replace", String.class,
                        builder.lower(published.get("text")), builder.literal("[^[:alnum:][:space:]]"), builder.literal(""), builder.literal("g"));
                staffMessage.select(builder.literal(1)).where(builder.equal(published.get("caseId"), root.get(ID)),
                        builder.like(normalizedStaffMessage, "%" + term + "%"));
                predicates.add(builder.or(builder.or(fields), builder.exists(memo), builder.exists(message), builder.exists(staffMessage)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate matchesTerm(Root<CaseEntity> root, CriteriaBuilder builder, String field, String term) {
        Expression<String> normalized = builder.function("regexp_replace", String.class,
                builder.lower(root.get(field)), builder.literal("[^[:alnum:][:space:]]"), builder.literal(""), builder.literal("g"));
        return builder.like(normalized, "%" + term + "%");
    }

    private <T> T authorizedRead(WebAccountContext context, Supplier<T> operation) {
        if (context == null) {
            throw failure(CaseErrorCode.UNAUTHENTICATED);
        }
        try {
            return reads.execute(transaction -> {
                StaffAccessDecision decision = access.checkStaffAccess(context);
                return switch (decision) {
                    case ALLOWED -> operation.get();
                    case UNAUTHENTICATED -> throw failure(CaseErrorCode.UNAUTHENTICATED);
                    case FORBIDDEN -> throw failure(CaseErrorCode.FORBIDDEN);
                };
            });
        } catch (StaffAuthenticationException exception) {
            if (exception.code() == StaffAuthenticationErrorCode.SERVICE_UNAVAILABLE) {
                throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
            }
            throw exception;
        } catch (DataAccessResourceFailureException | TransientDataAccessException | CannotCreateTransactionException exception) {
            throw failure(CaseErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private static CaseServiceException failure(CaseErrorCode code) {
        return new CaseServiceException(code, Map.of());
    }
}
