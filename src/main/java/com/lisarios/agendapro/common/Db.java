package com.lisarios.agendapro.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.time.*;
import java.util.*;

@Component
public class Db {
    public final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String,Object> result = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i=1; i<=meta.getColumnCount(); i++) {
                String key = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                var parts = key.split("_");
                StringBuilder camel = new StringBuilder(parts[0]);
                for (int p=1; p<parts.length; p++) camel.append(Character.toUpperCase(parts[p].charAt(0))).append(parts[p].substring(1));
                Object value = rs.getObject(i);
                if (value instanceof Timestamp ts) value = ts.toInstant();
                if (value instanceof OffsetDateTime odt) value = odt.toInstant();
                if (value instanceof Time t) value = t.toLocalTime().toString();
                result.put(camel.toString(), value);
            }
            return result;
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = rows(sql,args);
        if (rows.isEmpty()) throw Problem.notFound();
        return rows.getFirst();
    }
    public boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,args)); }
    public static UUID uuid(Object value) { return UUID.fromString(value.toString()); }
    public static Instant instant(Object value) { return value instanceof Instant i ? i : Instant.parse(value.toString()); }
}
