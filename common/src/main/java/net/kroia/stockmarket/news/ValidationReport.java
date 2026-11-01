package net.kroia.stockmarket.news;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result of a {@link NewsEventLibrary#reload()} pass.
 * <p>
 * The library follows the {@code MarketPreset} skip-and-continue philosophy:
 * a broken file or event never throws out of the loader and never crashes the
 * server. Instead, <b>every</b> problem found during a reload (syntax errors,
 * unknown fields, invalid numbers, duplicate ids, ...) is collected into this
 * report so the admin gets the complete picture in one pass instead of fixing
 * one error per reload attempt.
 * <p>
 * Severity contract:
 * <ul>
 *   <li>{@link Severity#ERROR} — the affected event (or file) was skipped or
 *       only partially applied (e.g. a duplicate id where the later definition
 *       won). The rest of the pool still loaded.</li>
 *   <li>{@link Severity#WARNING} — the event loaded, but something is
 *       suspicious (unknown field, unresolvable item, oversized texts, ...).</li>
 * </ul>
 */
public final class ValidationReport {

    /** Severity of a single report entry. See class Javadoc for the contract. */
    public enum Severity {
        ERROR,
        WARNING
    }

    /**
     * One problem found during a reload.
     *
     * @param severity whether the problem skipped content (ERROR) or is informational (WARNING)
     * @param file     the file name (not the full path) the problem was found in,
     *                 or {@code ""} for directory-level problems
     * @param eventId  the id of the affected event, or {@code null} for file-level problems
     *                 (or when the event has no readable id)
     * @param message  human-readable description of the problem
     */
    public record Entry(Severity severity, String file, @Nullable String eventId, String message) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(severity).append("] ");
            if (!file.isEmpty()) sb.append(file);
            if (eventId != null) sb.append(" (event '").append(eventId).append("')");
            sb.append(": ").append(message);
            return sb.toString();
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Adds an ERROR entry (content was skipped or only partially applied).
     *
     * @param file    source file name, or {@code ""} for directory-level problems
     * @param eventId affected event id, or {@code null} for file-level problems
     * @param message human-readable description
     */
    public void addError(String file, @Nullable String eventId, String message) {
        entries.add(new Entry(Severity.ERROR, file, eventId, message));
    }

    /**
     * Adds a WARNING entry (content loaded, but something is suspicious).
     *
     * @param file    source file name, or {@code ""} for directory-level problems
     * @param eventId affected event id, or {@code null} for file-level problems
     * @param message human-readable description
     */
    public void addWarning(String file, @Nullable String eventId, String message) {
        entries.add(new Entry(Severity.WARNING, file, eventId, message));
    }

    /** @return true if at least one ERROR entry was recorded */
    public boolean hasErrors() {
        return entries.stream().anyMatch(e -> e.severity() == Severity.ERROR);
    }

    /** @return true if at least one WARNING entry was recorded */
    public boolean hasWarnings() {
        return entries.stream().anyMatch(e -> e.severity() == Severity.WARNING);
    }

    /** @return number of ERROR entries */
    public int errorCount() {
        return (int) entries.stream().filter(e -> e.severity() == Severity.ERROR).count();
    }

    /** @return number of WARNING entries */
    public int warningCount() {
        return (int) entries.stream().filter(e -> e.severity() == Severity.WARNING).count();
    }

    /** @return all entries in the order they were recorded (unmodifiable) */
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** @return only the ERROR entries, in recording order */
    public List<Entry> getErrors() {
        return entries.stream().filter(e -> e.severity() == Severity.ERROR).toList();
    }

    /** @return only the WARNING entries, in recording order */
    public List<Entry> getWarnings() {
        return entries.stream().filter(e -> e.severity() == Severity.WARNING).toList();
    }

    /**
     * Groups all entries by their source file, preserving both the file order
     * and the entry order within each file. Directory-level entries are grouped
     * under the empty string key.
     *
     * @return an insertion-ordered map: file name → entries of that file
     */
    public Map<String, List<Entry>> byFile() {
        Map<String, List<Entry>> map = new LinkedHashMap<>();
        for (Entry entry : entries) {
            map.computeIfAbsent(entry.file(), k -> new ArrayList<>()).add(entry);
        }
        return map;
    }

    /** @return a one-line summary like {@code "2 error(s), 3 warning(s)"} */
    public String summary() {
        return errorCount() + " error(s), " + warningCount() + " warning(s)";
    }

    /** @return the summary followed by one line per entry (empty string if clean) */
    @Override
    public String toString() {
        if (entries.isEmpty()) return "0 error(s), 0 warning(s)";
        StringBuilder sb = new StringBuilder(summary());
        for (Entry entry : entries) {
            sb.append('\n').append(entry);
        }
        return sb.toString();
    }
}
