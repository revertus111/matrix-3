package game.atlas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.EvidenceStatus;

/**
 * Persistent curated Client Atlas knowledge.
 *
 * Generated symbols/relationships remain authoritative structural data. This
 * store only layers external aliases, notes/claims, evidence classification,
 * and supporting references over exact Atlas subject IDs. Curated records are
 * intentionally not reset by AtlasWorkspace.initialize()/rescans.
 */
public final class AtlasEvidenceStore {

    private static final int MAX_RECORDS = 5000;
    private static final int MAX_LINE_CHARS = 65536;
    private static final int MAX_ALIAS_CHARS = 240;
    private static final int MAX_CLAIM_CHARS = 4096;
    private static final int MAX_REFERENCES = 32;
    private static final int MAX_REFERENCE_CHARS = 1024;
    private static final int MAX_SEARCH_RESULTS = 200;

    private final AtlasWorkspace workspace;

    public AtlasEvidenceStore(AtlasWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        this.workspace = workspace;
    }

    /**
     * Load all curated knowledge in deterministic subject-id order.
     */
    public List<EvidenceRecord> load() throws IOException {
        workspace.ensureLayout();
        List<EvidenceRecord> records = loadMutable();
        sort(records);
        return Collections.unmodifiableList(records);
    }

    /**
     * Return the single curated record for an exact Atlas subject ID.
     */
    public EvidenceRecord get(String subjectId) throws IOException {
        String normalized = requireText(subjectId, "subjectId");
        for (EvidenceRecord record : load()) {
            if (record.getSubjectId().equals(normalized)) {
                return record;
            }
        }
        return null;
    }

    /**
     * Create or replace curated knowledge for one current Atlas symbol.
     *
     * The obfuscated symbol ID remains primary. The optional alias is external
     * metadata only and never renames generated Atlas symbols.
     */
    public EvidenceRecord upsert(AtlasInvestigationIndex index, String subjectId,
            EvidenceStatus status, String alias, String noteOrClaim,
            List<String> supportingReferences) throws IOException {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        String normalizedSubject = requireText(subjectId, "subjectId");
        SymbolEntry subject = index.getSymbol(normalizedSubject);
        if (subject == null) {
            throw new IOException("Cannot attach new Client Atlas evidence to an unknown current symbol: "
                    + normalizedSubject);
        }

        EvidenceRecord replacement = new EvidenceRecord(
                normalizedSubject,
                requireStatus(status),
                normalizeAlias(alias),
                normalizeClaim(noteOrClaim),
                normalizeReferences(supportingReferences),
                index.getMetadata().getClientFingerprint());
        validateRecord(replacement);

        workspace.ensureLayout();
        List<EvidenceRecord> records = loadMutable();
        boolean replaced = false;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).getSubjectId().equals(normalizedSubject)) {
                records.set(i, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (records.size() >= MAX_RECORDS) {
                throw new IOException("Client Atlas evidence store reached its " + MAX_RECORDS + " record limit");
            }
            records.add(replacement);
        }
        sort(records);
        writeAll(records);
        return replacement;
    }

    public boolean delete(String subjectId) throws IOException {
        String normalized = requireText(subjectId, "subjectId");
        workspace.ensureLayout();
        List<EvidenceRecord> records = loadMutable();
        boolean removed = false;
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).getSubjectId().equals(normalized)) {
                records.remove(i);
                removed = true;
            }
        }
        if (removed) {
            writeAll(records);
        }
        return removed;
    }

    /**
     * Evaluate every curated record against the current generated Atlas index.
     * Stale or missing subjects are retained and warned about rather than
     * silently deleted or reclassified.
     */
    public List<EvidenceView> inspect(AtlasInvestigationIndex index) throws IOException {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        String currentFingerprint = index.getMetadata().getClientFingerprint();
        List<EvidenceView> views = new ArrayList<EvidenceView>();
        for (EvidenceRecord record : load()) {
            views.add(evaluate(record, currentFingerprint, index.getSymbol(record.getSubjectId()) != null));
        }
        return Collections.unmodifiableList(views);
    }

    public EvidenceView getView(String subjectId, AtlasInvestigationIndex index) throws IOException {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        EvidenceRecord record = get(subjectId);
        if (record == null) {
            return null;
        }
        return evaluate(record, index.getMetadata().getClientFingerprint(),
                index.getSymbol(record.getSubjectId()) != null);
    }

    /**
     * Search curated aliases, notes/claims, subject IDs, and supporting refs.
     * This is intentionally separate from generated symbol search so evidence
     * never changes structural ranking or guessed semantics.
     */
    public List<EvidenceView> search(String query, AtlasInvestigationIndex index) throws IOException {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        String normalized = requireText(query, "query").toLowerCase(Locale.ROOT);
        List<EvidenceView> matches = new ArrayList<EvidenceView>();
        for (EvidenceView view : inspect(index)) {
            if (matches(view.getRecord(), normalized)) {
                matches.add(view);
                if (matches.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Shared freshness evaluation used by the store and verifier.
     */
    public static EvidenceView evaluate(EvidenceRecord record, String currentFingerprint,
            boolean subjectPresent) {
        if (record == null) {
            throw new IllegalArgumentException("record cannot be null");
        }
        String fingerprint = requireText(currentFingerprint, "currentFingerprint");
        return new EvidenceView(record,
                !record.getClientFingerprint().equals(fingerprint),
                subjectPresent,
                fingerprint);
    }

    private List<EvidenceRecord> loadMutable() throws IOException {
        List<EvidenceRecord> records = new ArrayList<EvidenceRecord>();
        Set<String> subjects = new LinkedHashSet<String>();
        Path path = workspace.evidenceFile();
        if (!Files.isRegularFile(path)) {
            return records;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().length() == 0) {
                    continue;
                }
                if (line.length() > MAX_LINE_CHARS) {
                    throw new IOException("Client Atlas evidence line exceeds " + MAX_LINE_CHARS
                            + " characters at " + path + ":" + lineNumber);
                }
                if (records.size() >= MAX_RECORDS) {
                    throw new IOException("Client Atlas evidence file exceeds " + MAX_RECORDS + " records: " + path);
                }
                EvidenceRecord record;
                try {
                    record = parseRecord(line);
                    validateRecord(record);
                } catch (RuntimeException ex) {
                    throw new IOException("Malformed Client Atlas evidence at " + path + ":" + lineNumber
                            + " - " + ex.getMessage(), ex);
                }
                if (!subjects.add(record.getSubjectId())) {
                    throw new IOException("Duplicate Client Atlas evidence subjectId at " + path + ":"
                            + lineNumber + " - " + record.getSubjectId());
                }
                records.add(record);
            }
        }
        return records;
    }

    private void writeAll(List<EvidenceRecord> records) throws IOException {
        if (records.size() > MAX_RECORDS) {
            throw new IOException("Client Atlas evidence store exceeds " + MAX_RECORDS + " records");
        }
        workspace.ensureLayout();
        Path target = workspace.evidenceFile();
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (EvidenceRecord record : records) {
                validateRecord(record);
                writer.write(AtlasJson.evidence(record));
                writer.newLine();
            }
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static EvidenceRecord parseRecord(String json) {
        String subjectId = JsonLine.requiredString(json, "subjectId");
        EvidenceStatus status = parseStatus(JsonLine.requiredString(json, "status"));
        String alias = JsonLine.optionalString(json, "alias");
        String claim = JsonLine.requiredString(json, "claim");
        List<String> references = JsonLine.stringArray(json, "supportingReferences");
        String fingerprint = JsonLine.requiredString(json, "clientFingerprint");
        return new EvidenceRecord(subjectId, status, alias, claim, references, fingerprint);
    }

    private static EvidenceStatus parseStatus(String value) {
        for (EvidenceStatus status : EvidenceStatus.values()) {
            if (status.name().equals(value) || status.getWireValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown evidence status " + value);
    }

    private static void validateRecord(EvidenceRecord record) {
        requireText(record.getSubjectId(), "subjectId");
        requireStatus(record.getStatus());
        String alias = record.getAlias();
        if (alias != null && alias.length() > MAX_ALIAS_CHARS) {
            throw new IllegalArgumentException("alias exceeds " + MAX_ALIAS_CHARS + " characters");
        }
        String claim = requireText(record.getClaim(), "claim");
        if (claim.length() > MAX_CLAIM_CHARS) {
            throw new IllegalArgumentException("claim exceeds " + MAX_CLAIM_CHARS + " characters");
        }
        List<String> references = record.getSupportingReferences();
        if (references.size() > MAX_REFERENCES) {
            throw new IllegalArgumentException("supportingReferences exceeds " + MAX_REFERENCES + " entries");
        }
        for (String reference : references) {
            String value = requireText(reference, "supportingReference");
            if (value.length() > MAX_REFERENCE_CHARS) {
                throw new IllegalArgumentException("supportingReference exceeds " + MAX_REFERENCE_CHARS
                        + " characters");
            }
        }
        requireText(record.getClientFingerprint(), "clientFingerprint");
    }

    private static String normalizeAlias(String alias) {
        if (alias == null) {
            return null;
        }
        String normalized = alias.trim();
        if (normalized.length() == 0) {
            return null;
        }
        if (normalized.length() > MAX_ALIAS_CHARS) {
            throw new IllegalArgumentException("alias exceeds " + MAX_ALIAS_CHARS + " characters");
        }
        return normalized;
    }

    private static String normalizeClaim(String claim) {
        String normalized = requireText(claim, "noteOrClaim").trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("noteOrClaim cannot be blank");
        }
        if (normalized.length() > MAX_CLAIM_CHARS) {
            throw new IllegalArgumentException("noteOrClaim exceeds " + MAX_CLAIM_CHARS + " characters");
        }
        return normalized;
    }

    private static List<String> normalizeReferences(List<String> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        for (String reference : references) {
            if (reference == null) {
                continue;
            }
            String normalized = reference.trim();
            if (normalized.length() == 0) {
                continue;
            }
            if (normalized.length() > MAX_REFERENCE_CHARS) {
                throw new IllegalArgumentException("supportingReference exceeds " + MAX_REFERENCE_CHARS
                        + " characters");
            }
            unique.add(normalized);
            if (unique.size() > MAX_REFERENCES) {
                throw new IllegalArgumentException("supportingReferences exceeds " + MAX_REFERENCES + " entries");
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(unique));
    }

    private static boolean matches(EvidenceRecord record, String query) {
        if (contains(record.getSubjectId(), query)
                || contains(record.getAlias(), query)
                || contains(record.getClaim(), query)
                || contains(record.getStatus().getWireValue(), query)) {
            return true;
        }
        for (String reference : record.getSupportingReferences()) {
            if (contains(reference, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static void sort(List<EvidenceRecord> records) {
        Collections.sort(records, new Comparator<EvidenceRecord>() {
            @Override
            public int compare(EvidenceRecord left, EvidenceRecord right) {
                return left.getSubjectId().compareTo(right.getSubjectId());
            }
        });
    }

    private static EvidenceStatus requireStatus(EvidenceStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        return status;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }

    public static final class EvidenceView {
        private final EvidenceRecord record;
        private final boolean staleFingerprint;
        private final boolean subjectPresent;
        private final String currentFingerprint;

        private EvidenceView(EvidenceRecord record, boolean staleFingerprint,
                boolean subjectPresent, String currentFingerprint) {
            this.record = record;
            this.staleFingerprint = staleFingerprint;
            this.subjectPresent = subjectPresent;
            this.currentFingerprint = currentFingerprint;
        }

        public EvidenceRecord getRecord() {
            return record;
        }

        public boolean isStaleFingerprint() {
            return staleFingerprint;
        }

        public boolean isSubjectPresent() {
            return subjectPresent;
        }

        public String getCurrentFingerprint() {
            return currentFingerprint;
        }

        public boolean isCurrent() {
            return !staleFingerprint && subjectPresent;
        }

        public String getFreshnessStatus() {
            if (staleFingerprint && !subjectPresent) {
                return "STALE_FINGERPRINT + SUBJECT_NOT_PRESENT";
            }
            if (staleFingerprint) {
                return "STALE_FINGERPRINT";
            }
            if (!subjectPresent) {
                return "SUBJECT_NOT_PRESENT";
            }
            return "CURRENT";
        }

        public String getWarning() {
            if (isCurrent()) {
                return null;
            }
            StringBuilder warning = new StringBuilder(192);
            if (staleFingerprint) {
                warning.append("Curated evidence was recorded against client fingerprint ")
                        .append(record.getClientFingerprint())
                        .append(" but the current Atlas fingerprint is ")
                        .append(currentFingerprint).append('.');
            }
            if (!subjectPresent) {
                if (warning.length() > 0) {
                    warning.append(' ');
                }
                warning.append("The exact subject ID is not present in the current Atlas index; record retained for review.");
            }
            return warning.toString();
        }
    }

    /** Deterministic parser for the evidence JSONL shape written by AtlasJson. */
    private static final class JsonLine {

        private static String requiredString(String json, String field) {
            String value = optionalString(json, field);
            if (value == null) {
                throw new IllegalArgumentException("missing/null string field " + field);
            }
            return value;
        }

        private static String optionalString(String json, String field) {
            int start = valueStart(json, field);
            if (startsWith(json, start, "null")) {
                return null;
            }
            ParsedString parsed = parseString(json, start, field);
            return parsed.value;
        }

        private static List<String> stringArray(String json, String field) {
            int index = valueStart(json, field);
            if (index >= json.length() || json.charAt(index) != '[') {
                throw new IllegalArgumentException("field " + field + " is not a JSON string array");
            }
            index = skipWhitespace(json, index + 1);
            List<String> values = new ArrayList<String>();
            if (index < json.length() && json.charAt(index) == ']') {
                return values;
            }
            while (index < json.length()) {
                ParsedString parsed = parseString(json, index, field);
                values.add(parsed.value);
                if (values.size() > MAX_REFERENCES) {
                    throw new IllegalArgumentException("field " + field + " exceeds " + MAX_REFERENCES + " entries");
                }
                index = skipWhitespace(json, parsed.nextIndex);
                if (index >= json.length()) {
                    break;
                }
                char delimiter = json.charAt(index);
                if (delimiter == ']') {
                    return values;
                }
                if (delimiter != ',') {
                    throw new IllegalArgumentException("invalid delimiter in field " + field);
                }
                index = skipWhitespace(json, index + 1);
            }
            throw new IllegalArgumentException("unterminated JSON string array field " + field);
        }

        private static ParsedString parseString(String json, int start, String field) {
            if (start >= json.length() || json.charAt(start) != '"') {
                throw new IllegalArgumentException("field " + field + " is not a JSON string");
            }
            StringBuilder decoded = null;
            int segmentStart = start + 1;
            for (int i = segmentStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    String value;
                    if (decoded == null) {
                        value = json.substring(segmentStart, i);
                    } else {
                        decoded.append(json, segmentStart, i);
                        value = decoded.toString();
                    }
                    return new ParsedString(value, i + 1);
                }
                if (c != '\\') {
                    continue;
                }
                if (decoded == null) {
                    decoded = new StringBuilder(i - segmentStart + 16);
                }
                decoded.append(json, segmentStart, i);
                if (++i >= json.length()) {
                    throw new IllegalArgumentException("unterminated escape in field " + field);
                }
                char escaped = json.charAt(i);
                switch (escaped) {
                case '"': decoded.append('"'); break;
                case '\\': decoded.append('\\'); break;
                case '/': decoded.append('/'); break;
                case 'b': decoded.append('\b'); break;
                case 'f': decoded.append('\f'); break;
                case 'n': decoded.append('\n'); break;
                case 'r': decoded.append('\r'); break;
                case 't': decoded.append('\t'); break;
                case 'u':
                    if (i + 4 >= json.length()) {
                        throw new IllegalArgumentException("short unicode escape in field " + field);
                    }
                    int code = 0;
                    for (int j = 1; j <= 4; j++) {
                        int digit = Character.digit(json.charAt(i + j), 16);
                        if (digit < 0) {
                            throw new IllegalArgumentException("invalid unicode escape in field " + field);
                        }
                        code = (code << 4) | digit;
                    }
                    decoded.append((char) code);
                    i += 4;
                    break;
                default:
                    throw new IllegalArgumentException("invalid escape in field " + field);
                }
                segmentStart = i + 1;
            }
            throw new IllegalArgumentException("unterminated JSON string field " + field);
        }

        private static int valueStart(String json, String field) {
            String token = "\"" + field + "\"";
            int fieldStart = json.indexOf(token);
            if (fieldStart < 0) {
                throw new IllegalArgumentException("missing field " + field);
            }
            int colon = skipWhitespace(json, fieldStart + token.length());
            if (colon >= json.length() || json.charAt(colon) != ':') {
                throw new IllegalArgumentException("missing ':' after field " + field);
            }
            return skipWhitespace(json, colon + 1);
        }

        private static int skipWhitespace(String value, int index) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            return index;
        }

        private static boolean startsWith(String value, int offset, String token) {
            return offset >= 0 && offset + token.length() <= value.length()
                    && value.regionMatches(offset, token, 0, token.length());
        }

        private static final class ParsedString {
            private final String value;
            private final int nextIndex;

            private ParsedString(String value, int nextIndex) {
                this.value = value;
                this.nextIndex = nextIndex;
            }
        }
    }
}
