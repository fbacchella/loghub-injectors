package fr.loghub.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record InsertParser(String tableName, List<String> columns, int parametersCount) {
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "^INSERT\\s+INTO\\s+([^\\s\\(]+)\\s*(?:\\(([^\\)]+)\\))?\\s+VALUES\\s*\\(([^\\)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );

    public static InsertParser parse(String sql) {
        Matcher matcher = INSERT_PATTERN.matcher(sql.trim());
        if (matcher.find()) {
            String tableName = matcher.group(1);
            String columnsPart = matcher.group(2);
            String valuesPart = matcher.group(3);

            List<String> columns;
            if (columnsPart != null) {
                columns = parseList(columnsPart);
            } else {
                columns = new ArrayList<>();
            }

            int parametersCount = countParameters(valuesPart);
            return new InsertParser(tableName, List.copyOf(columns), parametersCount);
        } else {
            throw new IllegalArgumentException("Invalid INSERT statement: " + sql);
        }
    }

    private static List<String> parseList(String list) {
        String[] parts = list.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            result.add(part.trim());
        }
        return result;
    }

    private static int countParameters(String values) {
        int count = 0;
        for (int i = 0; i < values.length(); i++) {
            if (values.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
