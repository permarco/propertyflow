ALTER TABLE pf_staff_decision
    DROP CONSTRAINT ck_pf_staff_decision_element,
    ALTER COLUMN task_element TYPE varchar(80);

UPDATE pf_staff_decision
SET task_element = CASE task_element
    WHEN 'SReview' THEN 'Fall_pruefen_und_Vorgehen_entscheiden_USERTASK'
    WHEN 'SWork' THEN 'Naechste_Mitarbeiteraktion_abwarten_USERTASK'
    WHEN 'SExternalWait' THEN 'Externe_Rueckmeldung_abwarten_USERTASK'
END;

ALTER TABLE pf_staff_decision
    ALTER COLUMN task_element SET DEFAULT 'Fall_pruefen_und_Vorgehen_entscheiden_USERTASK',
    ADD CONSTRAINT ck_pf_staff_decision_element CHECK (task_element IN (
        'Fall_pruefen_und_Vorgehen_entscheiden_USERTASK',
        'Naechste_Mitarbeiteraktion_abwarten_USERTASK',
        'Externe_Rueckmeldung_abwarten_USERTASK'
    ));

UPDATE pf_staff_message_command
SET task_element = ids.current_id
FROM (VALUES
    ('SReview', 'Fall_pruefen_und_Vorgehen_entscheiden_USERTASK'),
    ('SWork', 'Naechste_Mitarbeiteraktion_abwarten_USERTASK'),
    ('SExternalWait', 'Externe_Rueckmeldung_abwarten_USERTASK')
) AS ids(previous_id, current_id)
WHERE task_element = ids.previous_id;
