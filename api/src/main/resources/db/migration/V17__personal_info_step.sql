-- ==========================================================
-- V17: Add PERSONAL_INFO as mandatory core step
-- ==========================================================

do
$$
    begin
        -- Extend verification_steps step_type constraint to include PERSONAL_INFO
        if exists (select 1 from pg_constraint where conname = 'ck_verification_steps_step_type') then
            alter table verification_steps drop constraint ck_verification_steps_step_type;
        end if;

        alter table verification_steps
            add constraint ck_verification_steps_step_type
                check (step_type in (
                    'PERSONAL_INFO',
                    'DOC_OCR', 'FACE_MATCH', 'LIVENESS', 'AML_SCREEN',
                    'EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'AML_QUESTIONNAIRE'
                ));

        -- Extend check_results check_type constraint to include PERSONAL_INFO
        if exists (select 1 from pg_constraint where conname = 'ck_check_results_check_type') then
            alter table check_results drop constraint ck_check_results_check_type;
        end if;

        alter table check_results
            add constraint ck_check_results_check_type
                check (check_type in (
                    'PERSONAL_INFO',
                    'DOC_OCR', 'FACE_MATCH', 'LIVENESS', 'SANCTIONS', 'PEP',
                    'EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'AML_QUESTIONNAIRE'
                ));
    end
$$;