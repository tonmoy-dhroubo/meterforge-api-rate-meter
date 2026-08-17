package io.meterforge.controlplane.product.domain;

import io.meterforge.controlplane.common.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RoutePathPattern {

    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public enum SegmentType {
        STATIC,
        VARIABLE,
        TERMINAL_WILDCARD
    }

    public record Segment(SegmentType type, String value) {}

    private final String rawPattern;
    private final List<Segment> segments;

    public RoutePathPattern(String rawPattern) {
        this.rawPattern = validateAndNormalize(rawPattern);
        this.segments = parseSegments(this.rawPattern);
    }

    public static RoutePathPattern parse(String rawPattern) {
        return new RoutePathPattern(rawPattern);
    }

    public String getRawPattern() {
        return rawPattern;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public String getCanonicalSignature() {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            sb.append("/");
            switch (segment.type()) {
                case STATIC -> sb.append(segment.value().toLowerCase());
                case VARIABLE -> sb.append("{*}");
                case TERMINAL_WILDCARD -> sb.append("**");
            }
        }
        return sb.isEmpty() ? "/" : sb.toString();
    }

    private static String validateAndNormalize(String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidInputException("Path pattern cannot be blank", "INVALID_PATH_PATTERN");
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            throw new InvalidInputException("Path pattern must start with '/'", "INVALID_PATH_PATTERN");
        }
        if (trimmed.contains("//")) {
            throw new InvalidInputException("Path pattern cannot contain consecutive slashes '//'", "INVALID_PATH_PATTERN");
        }
        if (trimmed.contains(" ")) {
            throw new InvalidInputException("Path pattern cannot contain spaces", "INVALID_PATH_PATTERN");
        }
        return trimmed;
    }

    private static List<Segment> parseSegments(String path) {
        List<Segment> parsed = new ArrayList<>();
        String[] parts = path.substring(1).split("/");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            if (part.equals("**")) {
                if (i != parts.length - 1) {
                    throw new InvalidInputException("Wildcard '**' is only allowed as the terminal path segment", "INVALID_PATH_PATTERN");
                }
                parsed.add(new Segment(SegmentType.TERMINAL_WILDCARD, "**"));
            } else if (part.startsWith("{") && part.endsWith("}")) {
                String varName = part.substring(1, part.length() - 1);
                if (varName.isBlank() || !VARIABLE_NAME_PATTERN.matcher(varName).matches()) {
                    throw new InvalidInputException("Invalid path variable name '{" + varName + "}'. Must be alphanumeric.", "INVALID_PATH_PATTERN");
                }
                parsed.add(new Segment(SegmentType.VARIABLE, varName));
            } else if (part.contains("{") || part.contains("}") || part.contains("*")) {
                throw new InvalidInputException("Invalid segment syntax: '" + part + "'. Partial wildcards/variables not supported.", "INVALID_PATH_PATTERN");
            } else {
                parsed.add(new Segment(SegmentType.STATIC, part));
            }
        }

        return parsed;
    }
}
