package com.pertegato.propertyflow.caseprocessing.service;

import com.pertegato.propertyflow.access.contract.StaffAccessDecision;
import com.pertegato.propertyflow.access.contract.StaffAccountAccess;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationErrorCode;
import com.pertegato.propertyflow.access.contract.StaffAuthenticationException;
import com.pertegato.propertyflow.access.contract.WebAccountContext;
import com.pertegato.propertyflow.caseprocessing.contract.AccountCaseReading;
import com.pertegato.propertyflow.caseprocessing.contract.CaseErrorCode;
import com.pertegato.propertyflow.caseprocessing.contract.CaseServiceException;
import com.pertegato.propertyflow.caseprocessing.contract.CaseStatus;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseListing;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCasePage;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseQuery;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseSummary;
import com.pertegato.propertyflow.caseprocessing.contract.StaffCaseView;
import com.pertegato.propertyflow.caseprocessing.contract.TenantCaseView;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseEntity;
import com.pertegato.propertyflow.caseprocessing.persistence.CaseRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("propertyflow-backend & !frontend-demo")
class StaffCaseService implements StaffCaseListing, AccountCaseReading {

    private static final int PAGE_SIZE = 50;
    private static final String NOT_ASSESSED = "Noch nicht bewertet";
    private static final Set<String> URGENCIES = Set.of("", NOT_ASSESSED, "Kritisch", "Hoch", "Normal", "Niedrig");
    private static final String ID = "id";
    private static final String LAST_CONTACT = "lastTenantContactAt";
    private static final List<String> SEARCH_FIELDS = List.of(ID, "title", "originalUnit", "description");
    private static final Pattern NON_SEARCH_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}\\s]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "default", LAST_CONTACT, "title", "title", "unit", "originalUnit", "received", "receivedAt",
            "contact", LAST_CONTACT, "followUp", LAST_CONTACT, "urgency", LAST_CONTACT, "status", "status");

    private final StaffAccountAccess access;
    private final CaseRepository cases;
    private final TransactionTemplate reads;

    StaffCaseService(StaffAccountAccess access, CaseRepository cases, PlatformTransactionManager transactions) {
        this.access = access;
        this.cases = cases;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    @Override
    public StaffCaseView readForStaff(WebAccountContext context, String caseId) {
        return authorizedRead(context, () -> {
            CaseEntity stored = cases.findById(caseId).orElseThrow(() -> failure(CaseErrorCode.CASE_NOT_AVAILABLE));
            return new StaffCaseView(CaseProjections.tenantView(stored), stored.getContactEmail(),
                    Optional.empty(), List.of(), List.of());
        });
    }

    @Override
    public TenantCaseView readForTenant(WebAccountContext context, String caseId) {
        throw failure(CaseErrorCode.FORBIDDEN);
    }

    @Override
    public StaffCasePage listForStaff(WebAccountContext context, StaffCaseQuery query) {
        return authorizedRead(context, () -> findPage(query));
    }

    private StaffCasePage findPage(StaffCaseQuery query) {
        if (query.query().length() > 200 || !URGENCIES.contains(query.urgency()) || !SORT_PROPERTIES.containsKey(query.sort())) {
            throw failure(CaseErrorCode.INVALID_INPUT);
        }
        if (!query.urgency().isEmpty() && !NOT_ASSESSED.equals(query.urgency())) {
            return new StaffCasePage(List.of(), 0, 1, 1);
        }
        Specification<CaseEntity> matching = filters(query);
        long total = cases.count(matching);
        int totalPages = (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.ceil((double) total / PAGE_SIZE)));
        int page = Math.clamp(query.page(), 1, totalPages);
        if (total == 0) {
            return new StaffCasePage(List.of(), 0, page, totalPages);
        }
        Sort sort = Sort.by(query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, SORT_PROPERTIES.get(query.sort()))
                .and(Sort.by(ID));
        List<StaffCaseSummary> rows = cases.findBy(matching, selection -> selection.slice(PageRequest.of(page - 1, PAGE_SIZE, sort)))
                .map(stored -> new StaffCaseSummary(CaseProjections.state(stored), stored.getTitle(), stored.getOriginalUnit(),
                        stored.getReceivedAt(), stored.getLastTenantContactAt(), Optional.empty()))
                .getContent();
        return new StaffCasePage(rows, total, page, totalPages);
    }

    private Specification<CaseEntity> filters(StaffCaseQuery query) {
        String cleaned = NON_SEARCH_CHARACTER.matcher(query.query().toLowerCase(Locale.ROOT)).replaceAll("").strip();
        List<String> terms = cleaned.isBlank() ? List.of() : List.of(WORD_BOUNDARY.split(cleaned));
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!query.includeClosed()) {
                predicates.add(builder.notEqual(root.get("status"), CaseStatus.CLOSED));
            }
            for (String term : terms) {
                Predicate[] fields = SEARCH_FIELDS.stream()
                        .map(field -> matchesTerm(root, builder, field, term))
                        .toArray(Predicate[]::new);
                predicates.add(builder.or(fields));
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
