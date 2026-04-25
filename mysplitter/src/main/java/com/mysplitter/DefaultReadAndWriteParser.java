/*
 * Copyright 2018 BerryWang1996
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mysplitter;

import com.mysplitter.advise.ReadAndWriteParserAdvise;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative default read/write parser.
 */
public class DefaultReadAndWriteParser implements ReadAndWriteParserAdvise {

    private static final String READERS = "readers";

    private static final String WRITERS = "writers";

    private static final Pattern SELECT_FOR_UPDATE_PATTERN =
            Pattern.compile("\\bfor\\s+update\\b");

    private static final Pattern SELECT_FOR_SHARE_PATTERN =
            Pattern.compile("\\bfor\\s+share\\b");

    private static final Pattern LOCK_IN_SHARE_MODE_PATTERN =
            Pattern.compile("\\block\\s+in\\s+share\\s+mode\\b");

    private static final Pattern SELECT_INTO_FILE_PATTERN =
            Pattern.compile("\\binto\\s+(out|dump)file\\b");

    private static final Pattern SIDE_EFFECT_FUNCTION_PATTERN =
            Pattern.compile("\\b(nextval|setval|get_lock|release_lock)\\s*\\(");

    @Override
    public String parseOperation(String sql) {
        String statement = stripLeadingComments(sql);
        if (statement.length() == 0 || containsVendorHint(statement)) {
            return WRITERS;
        }

        String normalized = normalizeWhitespace(statement).toLowerCase(Locale.ENGLISH);
        if (normalized.startsWith("select ") || "select".equals(normalized)) {
            return isSafeSelect(normalized) ? READERS : WRITERS;
        }
        return WRITERS;
    }

    private boolean isSafeSelect(String normalizedSql) {
        return !SELECT_FOR_UPDATE_PATTERN.matcher(normalizedSql).find()
                && !SELECT_FOR_SHARE_PATTERN.matcher(normalizedSql).find()
                && !LOCK_IN_SHARE_MODE_PATTERN.matcher(normalizedSql).find()
                && !SELECT_INTO_FILE_PATTERN.matcher(normalizedSql).find()
                && !SIDE_EFFECT_FUNCTION_PATTERN.matcher(normalizedSql).find();
    }

    private String stripLeadingComments(String sql) {
        if (sql == null) {
            return "";
        }
        int index = 0;
        while (index < sql.length()) {
            index = skipWhitespace(sql, index);
            if (startsWith(sql, index, "--")) {
                index = skipLine(sql, index + 2);
            } else if (startsWith(sql, index, "#")) {
                index = skipLine(sql, index + 1);
            } else if (startsWith(sql, index, "/*")) {
                if (startsWith(sql, index, "/*+") || startsWith(sql, index, "/*!")) {
                    return sql.substring(index).trim();
                }
                int commentEnd = sql.indexOf("*/", index + 2);
                if (commentEnd < 0) {
                    return "";
                }
                index = commentEnd + 2;
            } else {
                return sql.substring(index).trim();
            }
        }
        return "";
    }

    private int skipWhitespace(String sql, int index) {
        while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
            index++;
        }
        return index;
    }

    private int skipLine(String sql, int index) {
        while (index < sql.length()) {
            char ch = sql.charAt(index++);
            if (ch == '\n' || ch == '\r') {
                break;
            }
        }
        return index;
    }

    private boolean startsWith(String sql, int index, String prefix) {
        return sql.regionMatches(index, prefix, 0, prefix.length());
    }

    private boolean containsVendorHint(String sql) {
        return sql.contains("/*+") || sql.contains("/*!");
    }

    private String normalizeWhitespace(String sql) {
        StringBuilder builder = new StringBuilder(sql.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousWhitespace) {
                    builder.append(' ');
                    previousWhitespace = true;
                }
            } else {
                builder.append(ch);
                previousWhitespace = false;
            }
        }
        return builder.toString().trim();
    }

}
