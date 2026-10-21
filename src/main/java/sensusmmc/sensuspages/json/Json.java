package sensusmmc.sensuspages.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private final String source;
    private int cursor;

    private Json(String source) {
        this.source = source;
    }

    public static Object parse(String source) {
        if (source == null) throw new IllegalArgumentException("source");
        Json parser = new Json(source);
        Object value = parser.value();
        parser.whitespace();
        if (parser.cursor != source.length()) throw parser.error("trailing data");
        return value;
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        append(value, builder);
        return builder.toString();
    }

    public static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(path + " must be an object");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    public static List<Object> array(Object value, String path) {
        if (!(value instanceof List)) throw new IllegalArgumentException(path + " must be an array");
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    public static String string(Object value, String path) {
        if (!(value instanceof String)) throw new IllegalArgumentException(path + " must be a string");
        return (String) value;
    }

    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public static String requiredString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(key + " must be a non-empty string");
        }
        return (String) value;
    }

    public static int integer(Object value, String path) {
        if (!(value instanceof Number)) throw new IllegalArgumentException(path + " must be a number");
        double number = ((Number) value).doubleValue();
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return (int) number;
    }

    public static int integer(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        return value instanceof Number ? integer(value, key) : fallback;
    }

    public static long longValue(Object value, String path) {
        if (!(value instanceof Number)) throw new IllegalArgumentException(path + " must be a number");
        return ((Number) value).longValue();
    }

    public static double decimal(Object value, String path) {
        if (!(value instanceof Number)) throw new IllegalArgumentException(path + " must be a number");
        return ((Number) value).doubleValue();
    }

    public static double decimal(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        return value instanceof Number ? decimal(value, key) : fallback;
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static List<String> strings(Object value, String path) {
        List<Object> array = array(value, path);
        List<String> result = new ArrayList<String>(array.size());
        for (int index = 0; index < array.size(); index += 1) {
            result.add(string(array.get(index), path + "[" + index + "]"));
        }
        return result;
    }

    private Object value() {
        whitespace();
        if (cursor >= source.length()) throw error("unexpected end");
        char current = source.charAt(cursor);
        if (current == '{') return object();
        if (current == '[') return array();
        if (current == '"') return string();
        if (current == 't') {
            literal("true");
            return Boolean.TRUE;
        }
        if (current == 'f') {
            literal("false");
            return Boolean.FALSE;
        }
        if (current == 'n') {
            literal("null");
            return null;
        }
        if (current == '-' || (current >= '0' && current <= '9')) return number();
        throw error("unexpected character");
    }

    private Map<String, Object> object() {
        cursor += 1;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        whitespace();
        if (accept('}')) return result;
        while (true) {
            whitespace();
            if (!peek('"')) throw error("object key");
            String key = string();
            whitespace();
            expect(':');
            result.put(key, value());
            whitespace();
            if (accept(',')) continue;
            if (accept('}')) return result;
            throw error("object separator");
        }
    }

    private List<Object> array() {
        cursor += 1;
        List<Object> result = new ArrayList<Object>();
        whitespace();
        if (accept(']')) return result;
        while (true) {
            result.add(value());
            whitespace();
            if (accept(',')) continue;
            if (accept(']')) return result;
            throw error("array separator");
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (cursor < source.length()) {
            char current = source.charAt(cursor++);
            if (current == '"') return result.toString();
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (cursor >= source.length()) throw error("unterminated escape");
            char escaped = source.charAt(cursor++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    result.append(escaped);
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    result.append(unicode());
                    break;
                default:
                    throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private char unicode() {
        if (cursor + 4 > source.length()) throw error("truncated unicode escape");
        String hex = source.substring(cursor, cursor + 4);
        cursor += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException error) {
            throw error("invalid unicode escape");
        }
    }

    private Number number() {
        int start = cursor;
        if (accept('-')) {
            if (cursor >= source.length()) throw error("truncated number");
        }
        if (accept('0')) {
            if (cursor < source.length() && Character.isDigit(source.charAt(cursor))) throw error("leading zero");
        } else {
            digits();
        }
        boolean decimal = false;
        if (accept('.')) {
            decimal = true;
            digits();
        }
        if (cursor < source.length() && (source.charAt(cursor) == 'e' || source.charAt(cursor) == 'E')) {
            decimal = true;
            cursor += 1;
            if (cursor < source.length() && (source.charAt(cursor) == '+' || source.charAt(cursor) == '-')) cursor += 1;
            digits();
        }
        String token = source.substring(start, cursor);
        try {
            return decimal ? (Number) Double.valueOf(token) : (Number) Long.valueOf(token);
        } catch (NumberFormatException error) {
            throw error("invalid number");
        }
    }

    private void digits() {
        int start = cursor;
        while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor += 1;
        if (start == cursor) throw error("expected digit");
    }

    private void literal(String literal) {
        if (!source.startsWith(literal, cursor)) throw error("expected " + literal);
        cursor += literal.length();
    }

    private void whitespace() {
        while (cursor < source.length()) {
            char current = source.charAt(cursor);
            if (current != ' ' && current != '\n' && current != '\r' && current != '\t') return;
            cursor += 1;
        }
    }

    private boolean peek(char expected) {
        return cursor < source.length() && source.charAt(cursor) == expected;
    }

    private boolean accept(char expected) {
        if (!peek(expected)) return false;
        cursor += 1;
        return true;
    }

    private void expect(char expected) {
        if (!accept(expected)) throw error("expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON at " + cursor + ": " + message);
    }

    private static void append(Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            appendString((String) value, builder);
        } else if (value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new IllegalArgumentException("non-finite number");
            }
            builder.append(value);
        } else if (value instanceof Map) {
            appendObject((Map<?, ?>) value, builder);
        } else if (value instanceof Iterable) {
            appendArray((Iterable<?>) value, builder);
        } else {
            throw new IllegalArgumentException("unsupported value " + value.getClass());
        }
    }

    private static void appendObject(Map<?, ?> value, StringBuilder builder) {
        builder.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("object key");
            if (!first) builder.append(',');
            first = false;
            appendString((String) entry.getKey(), builder);
            builder.append(':');
            append(entry.getValue(), builder);
        }
        builder.append('}');
    }

    private static void appendArray(Iterable<?> value, StringBuilder builder) {
        builder.append('[');
        boolean first = true;
        for (Object element : value) {
            if (!first) builder.append(',');
            first = false;
            append(element, builder);
        }
        builder.append(']');
    }

    private static void appendString(String value, StringBuilder builder) {
        builder.append('"');
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            switch (current) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
            }
        }
        builder.append('"');
    }
}
