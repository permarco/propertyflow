-- Initialschema des Moduls caseprocessing.
-- Konsolidierter Endstand der früheren Migrationen V1 bis V38; keine Bestandsmigration.

CREATE TABLE pf_acceptance_mail_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    access_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    CONSTRAINT pf_acceptance_mail_order_case_id_key UNIQUE (case_id),
    CONSTRAINT pf_acceptance_mail_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_answer_cancellation (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    message_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    leading_instance_key bigint NOT NULL,
    continuation_id uuid,
    target_status character varying(32),
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_answer_cancellation_message_id_key UNIQUE (message_id),
    CONSTRAINT pf_answer_cancellation_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_answer_clear_intent (
    command_id uuid NOT NULL,
    reply_call_id uuid,
    automatic_call_ref uuid NOT NULL,
    CONSTRAINT pf_answer_clear_intent_pkey PRIMARY KEY (command_id)
);

CREATE TABLE pf_assignment_event (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    actor_id uuid NOT NULL,
    previous_assignment character varying(200),
    current_assignment character varying(200) NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_assignment_event_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_automatic_closure (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    automatic_entry_id uuid NOT NULL,
    evaluation_id uuid NOT NULL,
    case_version bigint NOT NULL,
    analysis_version bigint NOT NULL,
    basis_hash character varying(64) NOT NULL,
    ground text NOT NULL,
    state character varying(16) NOT NULL,
    attempt_id uuid,
    started_at timestamp with time zone,
    publication_id uuid,
    published_version bigint,
    published_analysis_version bigint,
    continuation_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_automatic_closure_check CHECK (((state NOT IN ('PUBLISHED', 'CLOSED')) OR ((publication_id IS NOT NULL) AND (published_version IS NOT NULL) AND (published_analysis_version IS NOT NULL)))),
    CONSTRAINT pf_automatic_closure_state_check CHECK ((state IN ('PREPARED', 'RUNNING', 'PUBLISHED', 'CLOSED', 'REVIEW', 'STALE'))),
    CONSTRAINT pf_automatic_closure_automatic_entry_id_evaluation_id_key UNIQUE (automatic_entry_id, evaluation_id),
    CONSTRAINT pf_automatic_closure_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_automatic_entry (
    id uuid NOT NULL,
    call_ref uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    prepare_element_key bigint NOT NULL,
    case_version bigint NOT NULL,
    child_instance_key bigint,
    parent_element_key bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    effects_blocked boolean DEFAULT false NOT NULL,
    entry_kind character varying(16) DEFAULT 'INITIAL'::character varying NOT NULL,
    current_entry boolean DEFAULT true NOT NULL,
    reply_decision_id uuid,
    CONSTRAINT ck_pf_automatic_entry_kind CHECK ((entry_kind IN ('INITIAL', 'REOPEN', 'QUESTION', 'CLOSE'))),
    CONSTRAINT ck_pf_automatic_reply_origin CHECK (((entry_kind IN ('QUESTION', 'CLOSE')) = (reply_decision_id IS NOT NULL))),
    CONSTRAINT pf_automatic_entry_call_ref_key UNIQUE (call_ref),
    CONSTRAINT pf_automatic_entry_child_instance_key_key UNIQUE (child_instance_key),
    CONSTRAINT pf_automatic_entry_parent_element_key_key UNIQUE (parent_element_key),
    CONSTRAINT pf_automatic_entry_pkey PRIMARY KEY (id),
    CONSTRAINT pf_automatic_entry_prepare_element_key_key UNIQUE (prepare_element_key),
    CONSTRAINT pf_automatic_entry_reply_decision_id_key UNIQUE (reply_decision_id)
);

CREATE TABLE pf_automatic_question_batch (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    automatic_entry_id uuid NOT NULL,
    evaluation_id uuid NOT NULL,
    case_version bigint NOT NULL,
    catalog_version character varying(80) NOT NULL,
    question_ids text NOT NULL,
    state character varying(16) NOT NULL,
    reason character varying(80),
    continuation_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at timestamp with time zone,
    CONSTRAINT pf_automatic_question_batch_state_check CHECK ((state IN ('PREPARED', 'PUBLISHED', 'REVIEW', 'WAIT', 'STALE'))),
    CONSTRAINT pf_automatic_question_batch_automatic_entry_id_evaluation_i_key UNIQUE (automatic_entry_id, evaluation_id),
    CONSTRAINT pf_automatic_question_batch_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_case (
    id character varying(80) NOT NULL,
    title character varying(150) NOT NULL,
    original_unit character varying(200) NOT NULL,
    description text NOT NULL,
    contact_email character varying(254) NOT NULL,
    received_at timestamp with time zone NOT NULL,
    last_tenant_contact_at timestamp with time zone NOT NULL,
    status character varying(32) NOT NULL,
    initial_message_id uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    urgency character varying(16),
    urgency_source character varying(16),
    analysis_version bigint DEFAULT 0 NOT NULL,
    assigned_unit character varying(200),
    follow_up_due_at timestamp with time zone,
    CONSTRAINT ck_pf_case_status CHECK ((status IN ('RECEIVED', 'UNDER_CLARIFICATION', 'WAITING_FOR_TENANT', 'STAFF_REVIEW_REQUIRED', 'IN_PROGRESS', 'WAITING_FOR_EXTERNAL', 'WAITING_FOR_FOLLOW_UP', 'CLOSED'))),
    CONSTRAINT ck_pf_case_urgency_source CHECK (((urgency IS NULL) = (urgency_source IS NULL))),
    CONSTRAINT ck_pf_case_version CHECK ((version >= 0)),
    CONSTRAINT pf_case_assigned_unit_check CHECK ((length((assigned_unit)::text) > 0)),
    CONSTRAINT pf_case_urgency_check CHECK ((urgency IN ('CRITICAL', 'HIGH', 'NORMAL', 'LOW'))),
    CONSTRAINT pf_case_urgency_source_check CHECK ((urgency_source IN ('SYSTEM', 'STAFF'))),
    CONSTRAINT pf_case_initial_message_id_key UNIQUE (initial_message_id),
    CONSTRAINT pf_case_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_case_access (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    token_hash character varying(64) NOT NULL,
    encrypted_token text NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT pf_case_access_pkey PRIMARY KEY (id),
    CONSTRAINT pf_case_access_token_hash_key UNIQUE (token_hash),
    CONSTRAINT uk_pf_case_access_id_case UNIQUE (id, case_id)
);

CREATE TABLE pf_case_analysis (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    call_ref uuid NOT NULL,
    source_message_id uuid NOT NULL,
    input_version bigint NOT NULL,
    attempt_id uuid NOT NULL,
    started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone,
    state character varying(24) NOT NULL,
    system_urgency character varying(16),
    error_code character varying(80),
    prompt_version character varying(80) NOT NULL,
    job_key bigint,
    CONSTRAINT pf_case_analysis_job_key_check CHECK ((job_key > 0)),
    CONSTRAINT pf_case_analysis_state_check CHECK ((state IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED'))),
    CONSTRAINT pf_case_analysis_pkey PRIMARY KEY (id),
    CONSTRAINT uk_pf_initial_analysis UNIQUE (call_ref, input_version)
);

CREATE TABLE pf_case_closure (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    actor_id uuid,
    source character varying(16) NOT NULL,
    reason text NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    staff_decision_id uuid,
    follow_up_command_id uuid,
    case_data_command_id uuid,
    automatic_closure_id uuid,
    CONSTRAINT ck_pf_closure_origin CHECK (((num_nonnulls(staff_decision_id, follow_up_command_id, case_data_command_id, automatic_closure_id) = 1) AND ((((source)::text = 'STAFF'::text) AND (actor_id IS NOT NULL) AND (automatic_closure_id IS NULL)) OR (((source)::text = 'SYSTEM'::text) AND (actor_id IS NULL) AND (automatic_closure_id = id) AND (automatic_closure_id IS NOT NULL))))),
    CONSTRAINT pf_case_closure_automatic_closure_id_key UNIQUE (automatic_closure_id),
    CONSTRAINT pf_case_closure_case_data_command_id_key UNIQUE (case_data_command_id),
    CONSTRAINT pf_case_closure_case_id_leading_instance_key_key UNIQUE (case_id, leading_instance_key),
    CONSTRAINT pf_case_closure_follow_up_command_id_key UNIQUE (follow_up_command_id),
    CONSTRAINT pf_case_closure_pkey PRIMARY KEY (id),
    CONSTRAINT pf_case_closure_staff_decision_id_key UNIQUE (staff_decision_id)
);

CREATE TABLE pf_case_data_command (
    id uuid NOT NULL,
    operation_id character varying(80) NOT NULL,
    case_id character varying(80) NOT NULL,
    actor_id uuid NOT NULL,
    action_type character varying(32) NOT NULL,
    payload text NOT NULL,
    expected_version bigint NOT NULL,
    leading_instance_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    result_code character varying(80),
    result_version bigint,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone,
    reason text,
    request_fingerprint character varying(64),
    correlated boolean DEFAULT false NOT NULL,
    operation_reserved boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_pf_case_data_action CHECK ((action_type IN ('ADD_MEMO', 'SET_MANUAL_URGENCY', 'SET_OBJECT_ASSIGNMENT', 'CLEAR_ANSWER', 'TAKEOVER', 'CLOSE_CLARIFICATION', 'CLOSE_REPLY', 'DIRECT_FOLLOW_UP'))),
    CONSTRAINT pf_case_data_command_state_check CHECK ((state IN ('PENDING', 'APPLIED', 'REJECTED'))),
    CONSTRAINT pf_case_data_command_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_case_mail_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    event_id uuid NOT NULL,
    access_id uuid NOT NULL,
    notification_type character varying(32) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_case_mail_order_event_id_key UNIQUE (event_id),
    CONSTRAINT pf_case_mail_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_case_process (
    case_id character varying(80) NOT NULL,
    start_order_id uuid,
    process_instance_key bigint NOT NULL,
    definition_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    registered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reopen_order_id uuid,
    CONSTRAINT ck_pf_process_start_origin CHECK ((num_nonnulls(start_order_id, reopen_order_id) = 1)),
    CONSTRAINT pf_case_process_pkey PRIMARY KEY (case_id),
    CONSTRAINT pf_case_process_process_instance_key_key UNIQUE (process_instance_key),
    CONSTRAINT pf_case_process_reopen_order_id_key UNIQUE (reopen_order_id),
    CONSTRAINT pf_case_process_start_order_id_key UNIQUE (start_order_id)
);

CREATE TABLE pf_case_process_history (
    process_instance_key bigint NOT NULL,
    case_id character varying(80) NOT NULL,
    definition_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    start_order_id uuid,
    reopen_order_id uuid,
    registered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_case_process_history_check CHECK ((num_nonnulls(start_order_id, reopen_order_id) = 1)),
    CONSTRAINT pf_case_process_history_pkey PRIMARY KEY (process_instance_key)
);

CREATE TABLE pf_case_reopen_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    operation_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    previous_instance_key bigint NOT NULL,
    expected_version bigint NOT NULL,
    definition_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    state character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    new_instance_key bigint,
    continuation_id uuid,
    target_status character varying(32),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    CONSTRAINT pf_case_reopen_order_state_check CHECK ((state IN ('PENDING', 'APPLIED', 'REJECTED', 'BLOCKED'))),
    CONSTRAINT pf_case_reopen_order_case_id_actor_id_operation_id_key UNIQUE (case_id, actor_id, operation_id),
    CONSTRAINT pf_case_reopen_order_new_instance_key_key UNIQUE (new_instance_key),
    CONSTRAINT pf_case_reopen_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_case_review_signal (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    source_type character varying(32) NOT NULL,
    source_ref uuid NOT NULL,
    reason character varying(80) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    leading_instance_key bigint,
    continuation_id uuid,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    considered_decision_id uuid,
    considered_wait_command_id uuid,
    considered_direct_command_id uuid,
    CONSTRAINT ck_pf_review_signal_state CHECK ((state IN ('PENDING', 'CONFIRMED', 'CONSUMED', 'NOTE'))),
    CONSTRAINT pf_case_review_signal_pkey PRIMARY KEY (id),
    CONSTRAINT uk_pf_review_source UNIQUE (source_type, source_ref, reason)
);

CREATE TABLE pf_case_status_event (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    entry_id uuid,
    from_status character varying(32) NOT NULL,
    to_status character varying(32) NOT NULL,
    case_version bigint NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    continuation_id uuid,
    decision_id uuid,
    wait_resolution_id uuid,
    reopen_order_id uuid,
    review_signal_id uuid,
    wait_command_id uuid,
    reply_entry_id uuid,
    answer_cancellation_id uuid,
    takeover_id uuid,
    closure_command_id uuid,
    direct_command_id uuid,
    automatic_closure_id uuid,
    CONSTRAINT ck_pf_status_origin CHECK ((num_nonnulls(entry_id, continuation_id, decision_id, wait_resolution_id, reopen_order_id, review_signal_id, wait_command_id, reply_entry_id, answer_cancellation_id, takeover_id, closure_command_id, direct_command_id, automatic_closure_id) = 1)),
    CONSTRAINT pf_case_status_event_answer_cancellation_id_key UNIQUE (answer_cancellation_id),
    CONSTRAINT pf_case_status_event_automatic_closure_id_key UNIQUE (automatic_closure_id),
    CONSTRAINT pf_case_status_event_closure_command_id_key UNIQUE (closure_command_id),
    CONSTRAINT pf_case_status_event_continuation_id_key UNIQUE (continuation_id),
    CONSTRAINT pf_case_status_event_decision_id_key UNIQUE (decision_id),
    CONSTRAINT pf_case_status_event_direct_command_id_key UNIQUE (direct_command_id),
    CONSTRAINT pf_case_status_event_entry_id_key UNIQUE (entry_id),
    CONSTRAINT pf_case_status_event_pkey PRIMARY KEY (id),
    CONSTRAINT pf_case_status_event_reopen_order_id_key UNIQUE (reopen_order_id),
    CONSTRAINT pf_case_status_event_reply_entry_id_key UNIQUE (reply_entry_id),
    CONSTRAINT pf_case_status_event_review_signal_id_key UNIQUE (review_signal_id),
    CONSTRAINT pf_case_status_event_takeover_id_key UNIQUE (takeover_id),
    CONSTRAINT pf_case_status_event_wait_command_id_key UNIQUE (wait_command_id),
    CONSTRAINT pf_case_status_event_wait_resolution_id_key UNIQUE (wait_resolution_id)
);

CREATE TABLE pf_case_wait (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    staff_instance_key bigint,
    child_instance_key bigint,
    parent_element_key bigint,
    kind character varying(16) NOT NULL,
    due_at timestamp with time zone,
    state character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    ended_at timestamp with time zone,
    staff_decision_id uuid,
    change_command_id uuid,
    direct_command_id uuid,
    CONSTRAINT ck_pf_case_wait_state CHECK ((state IN ('ACTIVE', 'TIMER', 'CONTACT', 'CANCELLED', 'CLOSED', 'REPLACED', 'REVIEW'))),
    CONSTRAINT ck_pf_wait_origin CHECK ((num_nonnulls(staff_decision_id, change_command_id, direct_command_id) = 1)),
    CONSTRAINT pf_case_wait_kind_check CHECK ((kind IN ('FOLLOW_UP', 'EXTERNAL'))),
    CONSTRAINT pf_case_wait_change_command_id_key UNIQUE (change_command_id),
    CONSTRAINT pf_case_wait_direct_command_id_key UNIQUE (direct_command_id),
    CONSTRAINT pf_case_wait_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_clarification_question (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    automatic_call_ref uuid NOT NULL,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_clarification_question_automatic_call_ref_key UNIQUE (automatic_call_ref),
    CONSTRAINT pf_clarification_question_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_closure_publication (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    basis_hash character varying(64) NOT NULL,
    model text NOT NULL,
    prompt_version character varying(100) NOT NULL,
    validation_version character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_closure_publication_case_id_leading_instance_key_basis_h_key UNIQUE (case_id, leading_instance_key, basis_hash),
    CONSTRAINT pf_closure_publication_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_conversation_evaluation (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    case_version bigint NOT NULL,
    analysis_version bigint NOT NULL,
    phase character varying(32) NOT NULL,
    attempt_id uuid NOT NULL,
    state character varying(16) NOT NULL,
    started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    result_json text,
    applied_version bigint,
    applied_at timestamp with time zone,
    CONSTRAINT pf_conversation_evaluation_state_check CHECK ((state IN ('RUNNING', 'READY', 'APPLIED', 'SUPERSEDED'))),
    CONSTRAINT pf_conversation_evaluation_case_id_leading_instance_key_cas_key UNIQUE (case_id, leading_instance_key, case_version, analysis_version),
    CONSTRAINT pf_conversation_evaluation_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_direct_follow_up (
    id uuid NOT NULL,
    automatic_call_ref uuid NOT NULL,
    reply_call_id uuid,
    due_at timestamp with time zone NOT NULL,
    review_fingerprint character varying(64) NOT NULL,
    continuation_id uuid,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_direct_follow_up_continuation_id_key UNIQUE (continuation_id),
    CONSTRAINT pf_direct_follow_up_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_evaluation_delivery (
    evaluation_id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    case_version bigint NOT NULL,
    analysis_version bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_evaluation_delivery_pkey PRIMARY KEY (evaluation_id)
);

CREATE TABLE pf_follow_up_command (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    wait_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    operation_id uuid NOT NULL,
    expected_version bigint NOT NULL,
    review_fingerprint character varying(64) NOT NULL,
    action character varying(16) NOT NULL,
    request_input text NOT NULL,
    internal_reason text,
    due_at timestamp with time zone,
    state character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    correlation_attempted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    changed boolean DEFAULT false NOT NULL,
    conflict_continuation_id uuid,
    CONSTRAINT pf_follow_up_command_action_check CHECK ((action IN ('CHANGE', 'DELETE', 'CLOSE'))),
    CONSTRAINT pf_follow_up_command_state_check CHECK ((state IN ('PENDING', 'APPLYING', 'APPLIED', 'REJECTED'))),
    CONSTRAINT pf_follow_up_command_case_id_actor_id_operation_id_key UNIQUE (case_id, actor_id, operation_id),
    CONSTRAINT pf_follow_up_command_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_internal_memo (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    actor_id uuid NOT NULL,
    text text NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_internal_memo_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_mail_delivery (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    acceptance_order_id uuid,
    status_order_id uuid,
    notification_type character varying(32) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    attempt_id uuid,
    attempt_started_at timestamp with time zone,
    recipient character varying(254),
    accepted_at timestamp with time zone,
    error_code character varying(80),
    definition_key bigint,
    tenant_id character varying(255),
    process_instance_key bigint,
    start_state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    start_attempts integer DEFAULT 0 NOT NULL,
    next_start_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lease_id uuid,
    lease_until timestamp with time zone,
    last_start_result character varying(80),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    urgency_order_id uuid,
    message_order_id uuid,
    staff_message_order_id uuid,
    CONSTRAINT ck_pf_mail_attempts CHECK (((attempts >= 0) AND (start_attempts >= 0))),
    CONSTRAINT ck_pf_mail_origin CHECK ((num_nonnulls(acceptance_order_id, status_order_id, urgency_order_id, message_order_id, staff_message_order_id) = 1)),
    CONSTRAINT ck_pf_mail_start_state CHECK ((start_state IN ('PENDING', 'CONFIRMED', 'BLOCKED'))),
    CONSTRAINT ck_pf_mail_state CHECK ((state IN ('PENDING', 'SENDING', 'SENT', 'UNCERTAIN', 'FAILED'))),
    CONSTRAINT pf_mail_delivery_acceptance_order_id_key UNIQUE (acceptance_order_id),
    CONSTRAINT pf_mail_delivery_message_order_id_key UNIQUE (message_order_id),
    CONSTRAINT pf_mail_delivery_pkey PRIMARY KEY (id),
    CONSTRAINT pf_mail_delivery_process_instance_key_key UNIQUE (process_instance_key),
    CONSTRAINT pf_mail_delivery_staff_message_order_id_key UNIQUE (staff_message_order_id),
    CONSTRAINT pf_mail_delivery_status_order_id_key UNIQUE (status_order_id),
    CONSTRAINT pf_mail_delivery_urgency_order_id_key UNIQUE (urgency_order_id)
);

CREATE TABLE pf_message_mail_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_message_mail_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_process_start_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lease_id uuid,
    lease_until timestamp with time zone,
    definition_key bigint,
    tenant_id character varying(255),
    last_result character varying(80),
    CONSTRAINT ck_pf_process_start_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_pf_process_start_state CHECK ((state IN ('PENDING', 'CONFIRMED', 'BLOCKED'))),
    CONSTRAINT pf_process_start_order_case_id_key UNIQUE (case_id),
    CONSTRAINT pf_process_start_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_reply_call (
    id uuid NOT NULL,
    entry_id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    child_instance_key bigint NOT NULL,
    parent_element_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    state character varying(16) DEFAULT 'REGISTERING'::character varying NOT NULL,
    needs_review_delivery boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_reply_call_state_check CHECK ((state IN ('REGISTERING', 'WAITING', 'RETURNED', 'INTERRUPTED', 'CLOSING'))),
    CONSTRAINT pf_reply_call_child_instance_key_key UNIQUE (child_instance_key),
    CONSTRAINT pf_reply_call_parent_element_key_key UNIQUE (parent_element_key),
    CONSTRAINT pf_reply_call_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_reply_closure (
    id uuid NOT NULL,
    reply_call_id uuid NOT NULL,
    accepted boolean DEFAULT false NOT NULL,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    conflict_continuation_id uuid,
    CONSTRAINT pf_reply_closure_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_reply_decision (
    id uuid NOT NULL,
    call_id uuid NOT NULL,
    evaluation_id uuid,
    outcome character varying(16) NOT NULL,
    case_version bigint NOT NULL,
    continuation_id uuid,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_reply_decision_outcome_check CHECK ((outcome IN ('WAIT', 'REVIEW', 'QUESTION', 'CLOSE'))),
    CONSTRAINT pf_reply_decision_call_id_key UNIQUE (call_id),
    CONSTRAINT pf_reply_decision_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_reply_entry (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    source_staff_instance_key bigint,
    consumed_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    source_automatic_instance_key bigint,
    staff_message_id uuid,
    automatic_batch_id uuid,
    source_interrupted_automatic_instance_key bigint,
    CONSTRAINT ck_pf_reply_entry_origin CHECK ((((source_staff_instance_key IS NOT NULL) AND (staff_message_id IS NOT NULL) AND (automatic_batch_id IS NULL)) OR ((source_automatic_instance_key IS NOT NULL) AND (automatic_batch_id IS NOT NULL) AND (staff_message_id IS NULL)) OR ((source_interrupted_automatic_instance_key IS NOT NULL) AND (staff_message_id IS NOT NULL) AND (automatic_batch_id IS NULL)))),
    CONSTRAINT ck_pf_reply_source CHECK ((num_nonnulls(source_staff_instance_key, source_automatic_instance_key, source_interrupted_automatic_instance_key) = 1)),
    CONSTRAINT pf_reply_entry_automatic_batch_id_key UNIQUE (automatic_batch_id),
    CONSTRAINT pf_reply_entry_pkey PRIMARY KEY (id),
    CONSTRAINT pf_reply_entry_staff_message_id_key UNIQUE (staff_message_id)
);

CREATE TABLE pf_staff_answer_requirement (
    message_id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    state character varying(16) DEFAULT 'OPEN'::character varying NOT NULL,
    answered_message_id uuid,
    evaluation_id uuid,
    resolved_at timestamp with time zone,
    evidence text,
    resolution_type character varying(24),
    CONSTRAINT pf_staff_answer_requirement_resolution_type_check CHECK ((resolution_type IN ('ANSWERED', 'MANUAL', 'CASE_CLOSED'))),
    CONSTRAINT pf_staff_answer_requirement_state_check CHECK ((state IN ('OPEN', 'ANSWERED', 'CANCELLED'))),
    CONSTRAINT pf_staff_answer_requirement_pkey PRIMARY KEY (message_id)
);

CREATE TABLE pf_staff_continuation (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    automatic_call_ref uuid NOT NULL,
    case_version bigint NOT NULL,
    claim_element_key bigint,
    child_instance_key bigint,
    parent_element_key bigint,
    review_applied boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    superseded boolean DEFAULT false NOT NULL,
    origin character varying(32) DEFAULT 'AUTOMATIC_REVIEW'::character varying NOT NULL,
    entry_kind character varying(16) DEFAULT 'REVIEW'::character varying NOT NULL,
    CONSTRAINT ck_pf_staff_continuation_origin CHECK ((origin IN ('AUTOMATIC_REVIEW', 'CLOSE_CONFLICT', 'REOPEN', 'REPLY_REVIEW', 'ANSWER_CLEARED', 'TAKEOVER', 'DIRECT_FOLLOW_UP'))),
    CONSTRAINT pf_staff_continuation_entry_kind_check CHECK ((entry_kind IN ('REVIEW', 'CONTINUE', 'FOLLOW_UP'))),
    CONSTRAINT pf_staff_continuation_child_instance_key_key UNIQUE (child_instance_key),
    CONSTRAINT pf_staff_continuation_claim_element_key_key UNIQUE (claim_element_key),
    CONSTRAINT pf_staff_continuation_parent_element_key_key UNIQUE (parent_element_key),
    CONSTRAINT pf_staff_continuation_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_staff_decision (
    id uuid NOT NULL,
    operation_id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    continuation_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    user_task_key bigint NOT NULL,
    expected_version bigint NOT NULL,
    review_fingerprint character varying(64) NOT NULL,
    state character varying(24) DEFAULT 'PREPARED'::character varying NOT NULL,
    result_code character varying(80),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone,
    action character varying(16) DEFAULT 'CONTINUE'::character varying NOT NULL,
    task_element character varying(32) DEFAULT 'SReview'::character varying NOT NULL,
    internal_reason text,
    conflict_continuation_id uuid,
    CONSTRAINT ck_pf_staff_decision_action CHECK ((action IN ('CONTINUE', 'CLOSE', 'FOLLOW_UP', 'WAIT_EXTERNAL', 'QUESTION'))),
    CONSTRAINT ck_pf_staff_decision_element CHECK ((task_element IN ('SReview', 'SWork', 'SExternalWait'))),
    CONSTRAINT pf_staff_decision_state_check CHECK ((state IN ('PREPARED', 'CONFIRMED', 'APPLIED', 'REJECTED'))),
    CONSTRAINT pf_staff_decision_pkey PRIMARY KEY (id),
    CONSTRAINT uk_pf_staff_decision_operation UNIQUE (case_id, actor_id, operation_id)
);

CREATE TABLE pf_staff_message (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    author character varying(16) DEFAULT 'STAFF'::character varying NOT NULL,
    visibility character varying(16) DEFAULT 'EXTERNAL'::character varying NOT NULL,
    published boolean DEFAULT true NOT NULL,
    body text NOT NULL,
    answer_required boolean NOT NULL,
    analysis_version bigint NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    staff_command_id uuid,
    automatic_batch_id uuid,
    catalog_question_id character varying(16),
    closure_publication_id uuid,
    CONSTRAINT ck_pf_public_message_origin CHECK (((((author)::text = 'STAFF'::text) AND (staff_command_id = id) AND (staff_command_id IS NOT NULL) AND (automatic_batch_id IS NULL) AND (catalog_question_id IS NULL) AND (closure_publication_id IS NULL)) OR (((author)::text = 'SYSTEM'::text) AND (staff_command_id IS NULL) AND (automatic_batch_id IS NOT NULL) AND (catalog_question_id IS NOT NULL) AND (closure_publication_id IS NULL)) OR (((author)::text = 'SYSTEM'::text) AND (staff_command_id IS NULL) AND (automatic_batch_id IS NULL) AND (catalog_question_id IS NULL) AND (closure_publication_id = id) AND (closure_publication_id IS NOT NULL) AND (NOT answer_required)))),
    CONSTRAINT pf_staff_message_published_check CHECK (published),
    CONSTRAINT pf_staff_message_visibility_check CHECK (((visibility)::text = 'EXTERNAL'::text)),
    CONSTRAINT pf_staff_message_closure_publication_id_key UNIQUE (closure_publication_id),
    CONSTRAINT pf_staff_message_pkey PRIMARY KEY (id),
    CONSTRAINT pf_staff_message_staff_command_id_key UNIQUE (staff_command_id),
    CONSTRAINT uk_pf_automatic_question UNIQUE (case_id, catalog_question_id)
);

CREATE TABLE pf_staff_message_command (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    operation_id character varying(80) NOT NULL,
    actor_id uuid NOT NULL,
    expected_version bigint NOT NULL,
    leading_instance_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    target_instance_key bigint NOT NULL,
    parent_element_key bigint,
    user_task_key bigint,
    task_element character varying(80),
    automatic_call_ref uuid,
    body text NOT NULL,
    answer_required boolean NOT NULL,
    state character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    correlated boolean DEFAULT false NOT NULL,
    operation_reserved boolean DEFAULT true NOT NULL,
    result_code character varying(80),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    wait_id uuid,
    native_decision_id uuid,
    reply_call_id uuid,
    CONSTRAINT pf_staff_message_command_state_check CHECK ((state IN ('PENDING', 'PUBLISHED', 'REJECTED'))),
    CONSTRAINT pf_staff_message_command_native_decision_id_key UNIQUE (native_decision_id),
    CONSTRAINT pf_staff_message_command_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_staff_message_mail_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_staff_message_mail_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_staff_takeover (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    leading_instance_key bigint NOT NULL,
    automatic_call_ref uuid NOT NULL,
    continuation_id uuid,
    target_status character varying(32),
    applied_at timestamp with time zone,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_staff_takeover_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_staff_wait_request (
    decision_id uuid NOT NULL,
    due_at timestamp with time zone NOT NULL,
    CONSTRAINT pf_staff_wait_request_pkey PRIMARY KEY (decision_id)
);

CREATE TABLE pf_submission (
    id uuid NOT NULL,
    guest_scope uuid NOT NULL,
    operation_id character varying(80) NOT NULL,
    fingerprint character varying(64) NOT NULL,
    case_id character varying(80) NOT NULL,
    access_id uuid NOT NULL,
    CONSTRAINT pf_submission_pkey PRIMARY KEY (id),
    CONSTRAINT uk_pf_submission_scope_operation UNIQUE (guest_scope, operation_id)
);

CREATE TABLE pf_tenant_message (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    access_id uuid NOT NULL,
    operation_id character varying(80) NOT NULL,
    body text NOT NULL,
    author character varying(16) DEFAULT 'TENANT'::character varying NOT NULL,
    visibility character varying(16) DEFAULT 'EXTERNAL'::character varying NOT NULL,
    published boolean DEFAULT true NOT NULL,
    leading_instance_key bigint NOT NULL,
    tenant_id character varying(255) NOT NULL,
    analysis_version bigint NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    correlated boolean DEFAULT false NOT NULL,
    next_delivery_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    child_instance_key bigint,
    parent_element_key bigint,
    evaluation_id uuid,
    CONSTRAINT pf_tenant_message_author_check CHECK (((author)::text = 'TENANT'::text)),
    CONSTRAINT pf_tenant_message_body_check CHECK ((length(body) > 0)),
    CONSTRAINT pf_tenant_message_published_check CHECK (published),
    CONSTRAINT pf_tenant_message_state_check CHECK ((state IN ('PENDING', 'RECEIVED', 'PROCESSED', 'SUPERSEDED'))),
    CONSTRAINT pf_tenant_message_visibility_check CHECK (((visibility)::text = 'EXTERNAL'::text)),
    CONSTRAINT pf_tenant_message_pkey PRIMARY KEY (id),
    CONSTRAINT uk_pf_tenant_message_operation UNIQUE (case_id, operation_id)
);

CREATE TABLE pf_urgency_event (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    command_id uuid,
    previous_urgency character varying(16),
    current_urgency character varying(16) NOT NULL,
    source character varying(16) NOT NULL,
    actor_id uuid,
    internal_reason text,
    public_change boolean NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    analysis_id uuid,
    evaluation_id uuid,
    CONSTRAINT ck_pf_urgency_origin CHECK (((((source)::text = 'STAFF'::text) AND (command_id IS NOT NULL) AND (actor_id IS NOT NULL) AND (analysis_id IS NULL) AND (evaluation_id IS NULL)) OR (((source)::text = 'SYSTEM'::text) AND (command_id IS NULL) AND (actor_id IS NULL) AND (num_nonnulls(analysis_id, evaluation_id) = 1)))),
    CONSTRAINT pf_urgency_event_analysis_id_key UNIQUE (analysis_id),
    CONSTRAINT pf_urgency_event_command_id_key UNIQUE (command_id),
    CONSTRAINT pf_urgency_event_evaluation_id_key UNIQUE (evaluation_id),
    CONSTRAINT pf_urgency_event_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_urgency_mail_order (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    event_id uuid NOT NULL,
    state character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_urgency_mail_order_event_id_key UNIQUE (event_id),
    CONSTRAINT pf_urgency_mail_order_pkey PRIMARY KEY (id)
);

CREATE TABLE pf_wait_contact (
    message_id uuid NOT NULL,
    wait_id uuid NOT NULL,
    delivered boolean DEFAULT false NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_wait_contact_pkey PRIMARY KEY (message_id)
);

CREATE TABLE pf_wait_resolution (
    id uuid NOT NULL,
    case_id character varying(80) NOT NULL,
    cause character varying(16) NOT NULL,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pf_wait_resolution_pkey PRIMARY KEY (id)
);

-- Fremdschlüssel werden nach allen Tabellen angelegt, damit zyklische Fallbezüge zulässig sind.

ALTER TABLE ONLY pf_acceptance_mail_order
    ADD CONSTRAINT fk_pf_acceptance_mail_original_access FOREIGN KEY (access_id, case_id) REFERENCES pf_case_access(id, case_id);

ALTER TABLE ONLY pf_acceptance_mail_order
    ADD CONSTRAINT pf_acceptance_mail_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_answer_cancellation
    ADD CONSTRAINT pf_answer_cancellation_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_answer_cancellation
    ADD CONSTRAINT pf_answer_cancellation_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_answer_cancellation
    ADD CONSTRAINT pf_answer_cancellation_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_answer_cancellation
    ADD CONSTRAINT pf_answer_cancellation_message_id_fkey FOREIGN KEY (message_id) REFERENCES pf_staff_answer_requirement(message_id);

ALTER TABLE ONLY pf_answer_clear_intent
    ADD CONSTRAINT pf_answer_clear_intent_automatic_call_ref_fkey FOREIGN KEY (automatic_call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_answer_clear_intent
    ADD CONSTRAINT pf_answer_clear_intent_command_id_fkey FOREIGN KEY (command_id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_answer_clear_intent
    ADD CONSTRAINT pf_answer_clear_intent_reply_call_id_fkey FOREIGN KEY (reply_call_id) REFERENCES pf_reply_call(id);

ALTER TABLE ONLY pf_assignment_event
    ADD CONSTRAINT pf_assignment_event_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_assignment_event
    ADD CONSTRAINT pf_assignment_event_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_automatic_closure
    ADD CONSTRAINT pf_automatic_closure_automatic_entry_id_fkey FOREIGN KEY (automatic_entry_id) REFERENCES pf_automatic_entry(id);

ALTER TABLE ONLY pf_automatic_closure
    ADD CONSTRAINT pf_automatic_closure_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_automatic_closure
    ADD CONSTRAINT pf_automatic_closure_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_automatic_closure
    ADD CONSTRAINT pf_automatic_closure_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_automatic_closure
    ADD CONSTRAINT pf_automatic_closure_publication_id_fkey FOREIGN KEY (publication_id) REFERENCES pf_closure_publication(id);

ALTER TABLE ONLY pf_automatic_entry
    ADD CONSTRAINT pf_automatic_entry_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case_process(case_id);

ALTER TABLE ONLY pf_automatic_entry
    ADD CONSTRAINT pf_automatic_entry_reply_decision_id_fkey FOREIGN KEY (reply_decision_id) REFERENCES pf_reply_decision(id);

ALTER TABLE ONLY pf_automatic_question_batch
    ADD CONSTRAINT pf_automatic_question_batch_automatic_entry_id_fkey FOREIGN KEY (automatic_entry_id) REFERENCES pf_automatic_entry(id);

ALTER TABLE ONLY pf_automatic_question_batch
    ADD CONSTRAINT pf_automatic_question_batch_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_automatic_question_batch
    ADD CONSTRAINT pf_automatic_question_batch_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_automatic_question_batch
    ADD CONSTRAINT pf_automatic_question_batch_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_case_access
    ADD CONSTRAINT pf_case_access_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_analysis
    ADD CONSTRAINT pf_case_analysis_call_ref_fkey FOREIGN KEY (call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_case_analysis
    ADD CONSTRAINT pf_case_analysis_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_closure
    ADD CONSTRAINT pf_case_closure_automatic_closure_id_fkey FOREIGN KEY (automatic_closure_id) REFERENCES pf_automatic_closure(id);

ALTER TABLE ONLY pf_case_closure
    ADD CONSTRAINT pf_case_closure_case_data_command_id_fkey FOREIGN KEY (case_data_command_id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_case_closure
    ADD CONSTRAINT pf_case_closure_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_closure
    ADD CONSTRAINT pf_case_closure_follow_up_command_id_fkey FOREIGN KEY (follow_up_command_id) REFERENCES pf_follow_up_command(id);

ALTER TABLE ONLY pf_case_closure
    ADD CONSTRAINT pf_case_closure_staff_decision_id_fkey FOREIGN KEY (staff_decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_case_data_command
    ADD CONSTRAINT pf_case_data_command_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_mail_order
    ADD CONSTRAINT fk_pf_case_mail_access FOREIGN KEY (access_id, case_id) REFERENCES pf_case_access(id, case_id);

ALTER TABLE ONLY pf_case_mail_order
    ADD CONSTRAINT pf_case_mail_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_mail_order
    ADD CONSTRAINT pf_case_mail_order_event_id_fkey FOREIGN KEY (event_id) REFERENCES pf_case_status_event(id);

ALTER TABLE ONLY pf_case_process
    ADD CONSTRAINT pf_case_process_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_process
    ADD CONSTRAINT pf_case_process_reopen_order_id_fkey FOREIGN KEY (reopen_order_id) REFERENCES pf_case_reopen_order(id);

ALTER TABLE ONLY pf_case_process
    ADD CONSTRAINT pf_case_process_start_order_id_fkey FOREIGN KEY (start_order_id) REFERENCES pf_process_start_order(id);

ALTER TABLE ONLY pf_case_process_history
    ADD CONSTRAINT pf_case_process_history_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_process_history
    ADD CONSTRAINT pf_case_process_history_reopen_order_id_fkey FOREIGN KEY (reopen_order_id) REFERENCES pf_case_reopen_order(id);

ALTER TABLE ONLY pf_case_process_history
    ADD CONSTRAINT pf_case_process_history_start_order_id_fkey FOREIGN KEY (start_order_id) REFERENCES pf_process_start_order(id);

ALTER TABLE ONLY pf_case_reopen_order
    ADD CONSTRAINT pf_case_reopen_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_review_signal
    ADD CONSTRAINT pf_case_review_signal_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_review_signal
    ADD CONSTRAINT pf_case_review_signal_considered_decision_id_fkey FOREIGN KEY (considered_decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_case_review_signal
    ADD CONSTRAINT pf_case_review_signal_considered_direct_command_id_fkey FOREIGN KEY (considered_direct_command_id) REFERENCES pf_direct_follow_up(id);

ALTER TABLE ONLY pf_case_review_signal
    ADD CONSTRAINT pf_case_review_signal_considered_wait_command_id_fkey FOREIGN KEY (considered_wait_command_id) REFERENCES pf_follow_up_command(id);

ALTER TABLE ONLY pf_case_review_signal
    ADD CONSTRAINT pf_case_review_signal_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_answer_cancellation_id_fkey FOREIGN KEY (answer_cancellation_id) REFERENCES pf_answer_cancellation(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_automatic_closure_id_fkey FOREIGN KEY (automatic_closure_id) REFERENCES pf_automatic_closure(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_closure_command_id_fkey FOREIGN KEY (closure_command_id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_decision_id_fkey FOREIGN KEY (decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_direct_command_id_fkey FOREIGN KEY (direct_command_id) REFERENCES pf_direct_follow_up(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES pf_automatic_entry(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_reopen_order_id_fkey FOREIGN KEY (reopen_order_id) REFERENCES pf_case_reopen_order(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_reply_entry_id_fkey FOREIGN KEY (reply_entry_id) REFERENCES pf_reply_entry(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_review_signal_id_fkey FOREIGN KEY (review_signal_id) REFERENCES pf_case_review_signal(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_takeover_id_fkey FOREIGN KEY (takeover_id) REFERENCES pf_staff_takeover(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_wait_command_id_fkey FOREIGN KEY (wait_command_id) REFERENCES pf_follow_up_command(id);

ALTER TABLE ONLY pf_case_status_event
    ADD CONSTRAINT pf_case_status_event_wait_resolution_id_fkey FOREIGN KEY (wait_resolution_id) REFERENCES pf_wait_resolution(id);

ALTER TABLE ONLY pf_case_wait
    ADD CONSTRAINT pf_case_wait_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_case_wait
    ADD CONSTRAINT pf_case_wait_change_command_id_fkey FOREIGN KEY (change_command_id) REFERENCES pf_follow_up_command(id);

ALTER TABLE ONLY pf_case_wait
    ADD CONSTRAINT pf_case_wait_direct_command_id_fkey FOREIGN KEY (direct_command_id) REFERENCES pf_direct_follow_up(id);

ALTER TABLE ONLY pf_case_wait
    ADD CONSTRAINT pf_case_wait_staff_decision_id_fkey FOREIGN KEY (staff_decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_clarification_question
    ADD CONSTRAINT pf_clarification_question_automatic_call_ref_fkey FOREIGN KEY (automatic_call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_clarification_question
    ADD CONSTRAINT pf_clarification_question_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_clarification_question
    ADD CONSTRAINT pf_clarification_question_id_fkey FOREIGN KEY (id) REFERENCES pf_staff_message(id);

ALTER TABLE ONLY pf_closure_publication
    ADD CONSTRAINT pf_closure_publication_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_conversation_evaluation
    ADD CONSTRAINT pf_conversation_evaluation_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_direct_follow_up
    ADD CONSTRAINT pf_direct_follow_up_automatic_call_ref_fkey FOREIGN KEY (automatic_call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_direct_follow_up
    ADD CONSTRAINT pf_direct_follow_up_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_direct_follow_up
    ADD CONSTRAINT pf_direct_follow_up_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_direct_follow_up
    ADD CONSTRAINT pf_direct_follow_up_reply_call_id_fkey FOREIGN KEY (reply_call_id) REFERENCES pf_reply_call(id);

ALTER TABLE ONLY pf_evaluation_delivery
    ADD CONSTRAINT pf_evaluation_delivery_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_evaluation_delivery
    ADD CONSTRAINT pf_evaluation_delivery_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_follow_up_command
    ADD CONSTRAINT pf_follow_up_command_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_follow_up_command
    ADD CONSTRAINT pf_follow_up_command_conflict_continuation_id_fkey FOREIGN KEY (conflict_continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_follow_up_command
    ADD CONSTRAINT pf_follow_up_command_wait_id_fkey FOREIGN KEY (wait_id) REFERENCES pf_case_wait(id);

ALTER TABLE ONLY pf_internal_memo
    ADD CONSTRAINT pf_internal_memo_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_internal_memo
    ADD CONSTRAINT pf_internal_memo_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_acceptance_order_id_fkey FOREIGN KEY (acceptance_order_id) REFERENCES pf_acceptance_mail_order(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_message_order_id_fkey FOREIGN KEY (message_order_id) REFERENCES pf_message_mail_order(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_staff_message_order_id_fkey FOREIGN KEY (staff_message_order_id) REFERENCES pf_staff_message_mail_order(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_status_order_id_fkey FOREIGN KEY (status_order_id) REFERENCES pf_case_mail_order(id);

ALTER TABLE ONLY pf_mail_delivery
    ADD CONSTRAINT pf_mail_delivery_urgency_order_id_fkey FOREIGN KEY (urgency_order_id) REFERENCES pf_urgency_mail_order(id);

ALTER TABLE ONLY pf_message_mail_order
    ADD CONSTRAINT pf_message_mail_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_message_mail_order
    ADD CONSTRAINT pf_message_mail_order_id_fkey FOREIGN KEY (id) REFERENCES pf_tenant_message(id);

ALTER TABLE ONLY pf_process_start_order
    ADD CONSTRAINT pf_process_start_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_reply_call
    ADD CONSTRAINT pf_reply_call_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_reply_call
    ADD CONSTRAINT pf_reply_call_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES pf_reply_entry(id);

ALTER TABLE ONLY pf_reply_closure
    ADD CONSTRAINT pf_reply_closure_conflict_continuation_id_fkey FOREIGN KEY (conflict_continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_reply_closure
    ADD CONSTRAINT pf_reply_closure_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_reply_closure
    ADD CONSTRAINT pf_reply_closure_reply_call_id_fkey FOREIGN KEY (reply_call_id) REFERENCES pf_reply_call(id);

ALTER TABLE ONLY pf_reply_decision
    ADD CONSTRAINT pf_reply_decision_call_id_fkey FOREIGN KEY (call_id) REFERENCES pf_reply_call(id);

ALTER TABLE ONLY pf_reply_decision
    ADD CONSTRAINT pf_reply_decision_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_reply_decision
    ADD CONSTRAINT pf_reply_decision_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_reply_entry
    ADD CONSTRAINT pf_reply_entry_automatic_batch_id_fkey FOREIGN KEY (automatic_batch_id) REFERENCES pf_automatic_question_batch(id);

ALTER TABLE ONLY pf_reply_entry
    ADD CONSTRAINT pf_reply_entry_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_reply_entry
    ADD CONSTRAINT pf_reply_entry_staff_message_id_fkey FOREIGN KEY (staff_message_id) REFERENCES pf_staff_message(id);

ALTER TABLE ONLY pf_staff_answer_requirement
    ADD CONSTRAINT pf_staff_answer_requirement_answered_message_id_fkey FOREIGN KEY (answered_message_id) REFERENCES pf_tenant_message(id);

ALTER TABLE ONLY pf_staff_answer_requirement
    ADD CONSTRAINT pf_staff_answer_requirement_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_answer_requirement
    ADD CONSTRAINT pf_staff_answer_requirement_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_staff_answer_requirement
    ADD CONSTRAINT pf_staff_answer_requirement_message_id_fkey FOREIGN KEY (message_id) REFERENCES pf_staff_message(id);

ALTER TABLE ONLY pf_staff_continuation
    ADD CONSTRAINT pf_staff_continuation_automatic_call_ref_fkey FOREIGN KEY (automatic_call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_staff_continuation
    ADD CONSTRAINT pf_staff_continuation_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_decision
    ADD CONSTRAINT pf_staff_decision_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_decision
    ADD CONSTRAINT pf_staff_decision_conflict_continuation_id_fkey FOREIGN KEY (conflict_continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_staff_decision
    ADD CONSTRAINT pf_staff_decision_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_staff_message
    ADD CONSTRAINT pf_staff_message_automatic_batch_id_fkey FOREIGN KEY (automatic_batch_id) REFERENCES pf_automatic_question_batch(id);

ALTER TABLE ONLY pf_staff_message
    ADD CONSTRAINT pf_staff_message_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_message
    ADD CONSTRAINT pf_staff_message_closure_publication_id_fkey FOREIGN KEY (closure_publication_id) REFERENCES pf_closure_publication(id);

ALTER TABLE ONLY pf_staff_message
    ADD CONSTRAINT pf_staff_message_staff_command_id_fkey FOREIGN KEY (staff_command_id) REFERENCES pf_staff_message_command(id);

ALTER TABLE ONLY pf_staff_message_command
    ADD CONSTRAINT pf_staff_message_command_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_message_command
    ADD CONSTRAINT pf_staff_message_command_native_decision_id_fkey FOREIGN KEY (native_decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_staff_message_command
    ADD CONSTRAINT pf_staff_message_command_reply_call_id_fkey FOREIGN KEY (reply_call_id) REFERENCES pf_reply_call(id);

ALTER TABLE ONLY pf_staff_message_command
    ADD CONSTRAINT pf_staff_message_command_wait_id_fkey FOREIGN KEY (wait_id) REFERENCES pf_case_wait(id);

ALTER TABLE ONLY pf_staff_message_mail_order
    ADD CONSTRAINT pf_staff_message_mail_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_message_mail_order
    ADD CONSTRAINT pf_staff_message_mail_order_id_fkey FOREIGN KEY (id) REFERENCES pf_staff_message(id);

ALTER TABLE ONLY pf_staff_takeover
    ADD CONSTRAINT pf_staff_takeover_automatic_call_ref_fkey FOREIGN KEY (automatic_call_ref) REFERENCES pf_automatic_entry(call_ref);

ALTER TABLE ONLY pf_staff_takeover
    ADD CONSTRAINT pf_staff_takeover_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_staff_takeover
    ADD CONSTRAINT pf_staff_takeover_continuation_id_fkey FOREIGN KEY (continuation_id) REFERENCES pf_staff_continuation(id);

ALTER TABLE ONLY pf_staff_takeover
    ADD CONSTRAINT pf_staff_takeover_id_fkey FOREIGN KEY (id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_staff_wait_request
    ADD CONSTRAINT pf_staff_wait_request_decision_id_fkey FOREIGN KEY (decision_id) REFERENCES pf_staff_decision(id);

ALTER TABLE ONLY pf_submission
    ADD CONSTRAINT fk_pf_submission_original_access FOREIGN KEY (access_id, case_id) REFERENCES pf_case_access(id, case_id);

ALTER TABLE ONLY pf_submission
    ADD CONSTRAINT pf_submission_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_tenant_message
    ADD CONSTRAINT pf_tenant_message_access_id_fkey FOREIGN KEY (access_id) REFERENCES pf_case_access(id);

ALTER TABLE ONLY pf_tenant_message
    ADD CONSTRAINT pf_tenant_message_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_tenant_message
    ADD CONSTRAINT pf_tenant_message_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_urgency_event
    ADD CONSTRAINT pf_urgency_event_analysis_id_fkey FOREIGN KEY (analysis_id) REFERENCES pf_case_analysis(id);

ALTER TABLE ONLY pf_urgency_event
    ADD CONSTRAINT pf_urgency_event_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_urgency_event
    ADD CONSTRAINT pf_urgency_event_command_id_fkey FOREIGN KEY (command_id) REFERENCES pf_case_data_command(id);

ALTER TABLE ONLY pf_urgency_event
    ADD CONSTRAINT pf_urgency_event_evaluation_id_fkey FOREIGN KEY (evaluation_id) REFERENCES pf_conversation_evaluation(id);

ALTER TABLE ONLY pf_urgency_mail_order
    ADD CONSTRAINT pf_urgency_mail_order_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_urgency_mail_order
    ADD CONSTRAINT pf_urgency_mail_order_event_id_fkey FOREIGN KEY (event_id) REFERENCES pf_urgency_event(id);

ALTER TABLE ONLY pf_wait_contact
    ADD CONSTRAINT pf_wait_contact_message_id_fkey FOREIGN KEY (message_id) REFERENCES pf_tenant_message(id);

ALTER TABLE ONLY pf_wait_contact
    ADD CONSTRAINT pf_wait_contact_wait_id_fkey FOREIGN KEY (wait_id) REFERENCES pf_case_wait(id);

ALTER TABLE ONLY pf_wait_resolution
    ADD CONSTRAINT pf_wait_resolution_case_id_fkey FOREIGN KEY (case_id) REFERENCES pf_case(id);

ALTER TABLE ONLY pf_wait_resolution
    ADD CONSTRAINT pf_wait_resolution_id_fkey FOREIGN KEY (id) REFERENCES pf_case_wait(id);

-- Zusätzliche Suchindizes und bedingte Eindeutigkeitsregeln.

CREATE INDEX ix_pf_assignment_history ON pf_assignment_event USING btree (case_id, occurred_at);

CREATE UNIQUE INDEX uk_pf_current_automatic_entry ON pf_automatic_entry USING btree (case_id, leading_instance_key) WHERE current_entry;

CREATE UNIQUE INDEX uk_pf_case_access_active_case ON pf_case_access USING btree (case_id) WHERE (revoked_at IS NULL);

CREATE INDEX ix_pf_case_data_due ON pf_case_data_command USING btree (next_attempt_at) WHERE ((state)::text = 'PENDING'::text);

CREATE UNIQUE INDEX uk_pf_case_data_operation ON pf_case_data_command USING btree (case_id, operation_id) WHERE operation_reserved;

CREATE UNIQUE INDEX uk_pf_reopen_generation ON pf_case_reopen_order USING btree (case_id, previous_instance_key) WHERE ((state)::text <> 'REJECTED'::text);

CREATE INDEX ix_pf_review_signal_due ON pf_case_review_signal USING btree (next_attempt_at) WHERE ((state)::text = 'PENDING'::text);

CREATE UNIQUE INDEX uk_pf_active_case_wait ON pf_case_wait USING btree (case_id) WHERE ((state)::text = 'ACTIVE'::text);

CREATE UNIQUE INDEX uk_pf_active_wait_child ON pf_case_wait USING btree (child_instance_key) WHERE ((state)::text = 'ACTIVE'::text);

CREATE UNIQUE INDEX uk_pf_pending_follow_up_command ON pf_follow_up_command USING btree (case_id) WHERE (state IN ('PENDING', 'APPLYING'));

CREATE INDEX ix_pf_internal_memo_case ON pf_internal_memo USING btree (case_id, occurred_at);

CREATE INDEX ix_pf_mail_start_due ON pf_mail_delivery USING btree (next_start_at) WHERE ((start_state)::text = 'PENDING'::text);

CREATE INDEX ix_pf_process_start_due ON pf_process_start_order USING btree (next_attempt_at) WHERE ((state)::text = 'PENDING'::text);

CREATE UNIQUE INDEX uk_pf_reply_active ON pf_reply_call USING btree (case_id) WHERE (state IN ('REGISTERING', 'WAITING'));

CREATE UNIQUE INDEX uk_pf_reply_closure_accepted ON pf_reply_closure USING btree (reply_call_id) WHERE accepted;

CREATE UNIQUE INDEX uk_pf_current_staff_continuation ON pf_staff_continuation USING btree (case_id, automatic_call_ref) WHERE (NOT superseded);

CREATE INDEX ix_pf_staff_decision_task ON pf_staff_decision USING btree (user_task_key);

CREATE INDEX ix_pf_staff_message_history ON pf_staff_message USING btree (case_id, occurred_at, id);

CREATE UNIQUE INDEX uk_pf_staff_message_operation ON pf_staff_message_command USING btree (case_id, operation_id) WHERE operation_reserved;

CREATE INDEX ix_pf_tenant_message_delivery ON pf_tenant_message USING btree (next_delivery_at) WHERE ((state)::text = 'PENDING'::text);

CREATE INDEX ix_pf_tenant_message_history ON pf_tenant_message USING btree (case_id, occurred_at, id);

CREATE INDEX ix_pf_urgency_history ON pf_urgency_event USING btree (case_id, occurred_at);
