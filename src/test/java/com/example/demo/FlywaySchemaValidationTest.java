package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated Flyway vs. JPA Entity Schema Validation Test.
 *
 * Production runs PostgreSQL with spring.jpa.hibernate.ddl-auto=validate.
 * This test scans every @Entity in com.example.demo.entity and verifies that
 * every table and column exists in src/main/resources/db/migration/V*.sql,
 * preventing missing-migration 502 startup crashes in production.
 */
class FlywaySchemaValidationTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+(?:_\\d+)*)__.*\\.sql$");
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\"?)([a-zA-Z0-9_]+)(?:\"?)\\s*\\((.*?)\\);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ALTER_TABLE_STMT_PATTERN = Pattern.compile(
            "ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(?:\"?)([a-zA-Z0-9_]+)(?:\"?)(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ADD_COL_CLAUSE_PATTERN = Pattern.compile(
            "ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\"?)([a-zA-Z0-9_]+)(?:\"?)",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    @DisplayName("Flyway migrations have unique versions and cover every JPA @Entity table and column")
    void allJpaEntitiesAndColumnsExistInFlywayMigrations() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] migrationResources = resolver.getResources("classpath:db/migration/V*.sql");
        assertTrue(migrationResources.length > 0, "No Flyway migration files found in db/migration/");

        // 1. Verify no duplicate Flyway version numbers
        Map<String, String> seenVersions = new HashMap<>();
        Map<String, Set<String>> schemaTables = new HashMap<>();

        for (Resource res : migrationResources) {
            String filename = res.getFilename();
            assertNotNull(filename);
            Matcher vm = VERSION_PATTERN.matcher(filename);
            assertTrue(vm.matches(), "Invalid Flyway migration filename: " + filename);
            String version = vm.group(1);
            assertFalse(seenVersions.containsKey(version),
                    "Duplicate Flyway migration version V" + version + ": " + seenVersions.get(version) + " and " + filename);
            seenVersions.put(version, filename);

            String sql = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // Strip single-line SQL comments
            sql = sql.replaceAll("--[^\\r\\n]*", "");

            // Extract CREATE TABLE blocks and their column names
            Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(sql);
            while (createMatcher.find()) {
                String tableName = createMatcher.group(1).toLowerCase(Locale.ROOT);
                String body = createMatcher.group(2);
                Set<String> cols = schemaTables.computeIfAbsent(tableName, k -> new HashSet<>());
                for (String part : splitTopLevelCommas(body)) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) continue;
                    String upper = trimmed.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("CONSTRAINT ") || upper.startsWith("PRIMARY KEY")
                            || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
                            || upper.startsWith("CHECK")) {
                        continue;
                    }
                    String colToken = trimmed.split("\\s+")[0].replace("\"", "").toLowerCase(Locale.ROOT);
                    if (!colToken.isEmpty()) {
                        cols.add(colToken);
                    }
                }
            }

            // Extract ALTER TABLE ... ADD [COLUMN] ... (supports multiple comma-separated ADD COLUMN clauses)
            Matcher alterStmtMatcher = ALTER_TABLE_STMT_PATTERN.matcher(sql);
            while (alterStmtMatcher.find()) {
                String tableName = alterStmtMatcher.group(1).toLowerCase(Locale.ROOT);
                String alterBody = alterStmtMatcher.group(2);
                Matcher addColMatcher = ADD_COL_CLAUSE_PATTERN.matcher(alterBody);
                while (addColMatcher.find()) {
                    String colName = addColMatcher.group(1).toLowerCase(Locale.ROOT);
                    if (!"constraint".equals(colName) && !"primary".equals(colName) && !"unique".equals(colName) && !"foreign".equals(colName)) {
                        schemaTables.computeIfAbsent(tableName, k -> new HashSet<>()).add(colName);
                    }
                }
            }
        }

        // 2. Scan all @Entity classes in com.example.demo.entity
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<BeanDefinition> entityDefs = scanner.findCandidateComponents("com.example.demo.entity");
        assertFalse(entityDefs.isEmpty(), "Expected JPA @Entity classes in com.example.demo.entity");

        List<String> violations = new ArrayList<>();

        for (BeanDefinition def : entityDefs) {
            Class<?> entityClass = Class.forName(def.getBeanClassName());
            Table tableAnn = entityClass.getAnnotation(Table.class);
            String tableName = (tableAnn != null && !tableAnn.name().isBlank())
                    ? tableAnn.name().toLowerCase(Locale.ROOT)
                    : toSnakeCase(entityClass.getSimpleName());

            Set<String> migratedCols = schemaTables.get(tableName);
            if (migratedCols == null) {
                violations.add("Entity " + entityClass.getSimpleName() + " maps to table '" + tableName
                        + "', which is NOT created by any Flyway migration in db/migration/V*.sql");
                continue;
            }

            for (Field field : entityClass.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                String colName = resolveColumnName(field);
                if (!migratedCols.contains(colName)) {
                    violations.add("Entity " + entityClass.getSimpleName() + "." + field.getName()
                            + " maps to column '" + tableName + "." + colName
                            + "', which is MISSING from db/migration/V*.sql");
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Flyway schema validation failed (would cause ddl-auto=validate 502 crash in production):\n - "
                        + String.join("\n - ", violations));
    }

    private static String resolveColumnName(Field field) {
        JoinColumn jc = field.getAnnotation(JoinColumn.class);
        if (jc != null && !jc.name().isBlank()) {
            return jc.name().toLowerCase(Locale.ROOT);
        }
        Column col = field.getAnnotation(Column.class);
        if (col != null && !col.name().isBlank()) {
            return col.name().toLowerCase(Locale.ROOT);
        }
        return toSnakeCase(field.getName());
    }

    private static String toSnakeCase(String input) {
        return input.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static List<String> splitTopLevelCommas(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }
}
