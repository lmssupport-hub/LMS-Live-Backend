package com.example.lms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleConstraintFixer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        List<String> oldConstraints = jdbc.queryForList("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY (con.conkey)
                WHERE con.conrelid = 'roles'::regclass
                  AND con.contype = 'u'
                GROUP BY con.conname
                HAVING array_agg(a.attname::text) = ARRAY['name']
                """, String.class);

        for (String name : oldConstraints) {
            log.warn("Dropping old name-only constraint on roles: {}", name);
            jdbc.execute("ALTER TABLE roles DROP CONSTRAINT \"" + name + "\"");
        }

        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_owner_name_ci
                ON roles (created_by_admin_id, LOWER(name))
                """);
    }
}