ALTER TABLE solicitud_lectura_ia_job
    DROP FOREIGN KEY fk_sol_lectura_ia_job_solicitud;

ALTER TABLE solicitud_lectura_ia_job
    ADD CONSTRAINT fk_sol_lectura_ia_job_solicitud
        FOREIGN KEY (solicitud_id) REFERENCES solicitud(id) ON DELETE CASCADE;
