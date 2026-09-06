package game.atlas;

import java.util.List;

import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.RelationshipRecord;
import game.atlas.AtlasSchema.SymbolRecord;

/**
 * Deterministic JSON serialization helpers for generated Atlas records and
 * compact query/export output.
 */
final class AtlasJson {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private AtlasJson() {
    }

    static String symbol(SymbolRecord record) {
        StringBuilder builder = new StringBuilder(288);
        builder.append('{');
        appendStringField(builder, "id", record.getId(), true);
        appendStringField(builder, "kind", record.getKind().name(), false);
        appendStringField(builder, "owner", record.getOwner(), false);
        appendStringField(builder, "name", record.getName(), false);
        appendStringField(builder, "descriptor", record.getDescriptor(), false);
        appendStringField(builder, "signature", record.getSignature(), false);
        appendStringField(builder, "compiledPath", record.getCompiledPath(), false);
        appendStringField(builder, "sourcePath", record.getSourcePath(), false);
        appendNumberField(builder, "access", record.getAccess());
        builder.append('}');
        return builder.toString();
    }

    static String relationship(RelationshipRecord record) {
        StringBuilder builder = new StringBuilder(256);
        builder.append('{');
        appendStringField(builder, "fromId", record.getFromId(), true);
        appendStringField(builder, "type", record.getType().name(), false);
        appendStringField(builder, "target", record.getTarget(), false);
        appendStringField(builder, "sourcePath", record.getSourcePath(), false);
        appendNullableNumberField(builder, "sourceLine", record.getSourceLine());
        appendNullableNumberField(builder, "opcode", record.getOpcode());
        appendNumberField(builder, "occurrenceCount", record.getOccurrenceCount());
        appendStringField(builder, "detail", record.getDetail(), false);
        builder.append('}');
        return builder.toString();
    }

    static String evidence(EvidenceRecord record) {
        StringBuilder builder = new StringBuilder(512);
        builder.append('{');
        appendStringField(builder, "subjectId", record.getSubjectId(), true);
        appendStringField(builder, "status", record.getStatus().getWireValue(), false);
        appendEvidenceStringField(builder, "alias", record.getAlias());
        appendEvidenceStringField(builder, "claim", record.getClaim());
        appendEvidenceStringArrayField(builder, "supportingReferences", record.getSupportingReferences());
        appendStringField(builder, "clientFingerprint", record.getClientFingerprint(), false);
        builder.append('}');
        return builder.toString();
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(value.length() + 2);
        appendString(builder, value);
        return builder.toString();
    }

    private static void appendStringField(StringBuilder builder, String name, String value, boolean first) {
        if (!first) {
            builder.append(',');
        }
        appendString(builder, name);
        builder.append(':');
        if (value == null) {
            builder.append("null");
        } else {
            appendString(builder, value);
        }
    }

    /**
     * Curated evidence is parsed by a small deterministic field parser rather
     * than a general JSON library. Encode literal quote characters in free-form
     * evidence values as unicode escapes so text such as "clientFingerprint"
     * can never be mistaken for a later JSON property name.
     */
    private static void appendEvidenceStringField(StringBuilder builder, String name, String value) {
        builder.append(',');
        appendString(builder, name);
        builder.append(':');
        if (value == null) {
            builder.append("null");
        } else {
            appendEvidenceString(builder, value);
        }
    }

    private static void appendEvidenceStringArrayField(StringBuilder builder, String name, List<String> values) {
        builder.append(',');
        appendString(builder, name);
        builder.append(':').append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendEvidenceString(builder, values.get(i));
        }
        builder.append(']');
    }

    private static void appendNumberField(StringBuilder builder, String name, int value) {
        builder.append(',');
        appendString(builder, name);
        builder.append(':').append(value);
    }

    private static void appendNullableNumberField(StringBuilder builder, String name, Integer value) {
        builder.append(',');
        appendString(builder, name);
        builder.append(':');
        if (value == null) {
            builder.append("null");
        } else {
            builder.append(value.intValue());
        }
    }

    private static void appendEvidenceString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                appendUnicodeEscape(builder, c);
                continue;
            }
            appendEscapedCharacter(builder, c);
        }
        builder.append('"');
    }

    private static void appendString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendEscapedCharacter(builder, value.charAt(i));
        }
        builder.append('"');
    }

    private static void appendEscapedCharacter(StringBuilder builder, char c) {
        switch (c) {
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
            if (c < 0x20 || Character.isSurrogate(c)) {
                appendUnicodeEscape(builder, c);
            } else {
                builder.append(c);
            }
            break;
        }
    }

    private static void appendUnicodeEscape(StringBuilder builder, char c) {
        builder.append("\\u");
        builder.append(HEX[(c >>> 12) & 0xf]);
        builder.append(HEX[(c >>> 8) & 0xf]);
        builder.append(HEX[(c >>> 4) & 0xf]);
        builder.append(HEX[c & 0xf]);
    }
}
