package de.jeff_media.BestTools.network.redis;

/**
 * Redis message types and encoding/decoding helpers.
 * <p>
 * Wire format: {@code TYPE:payload}
 * <ul>
 *   <li>{@code SETTINGS_UPDATE:uuid:field:value}</li>
 *   <li>{@code BLACKLIST_UPDATE:uuid:action:mat1,mat2,...}</li>
 * </ul>
 */
public enum RedisMessage {
    SETTINGS_UPDATE,
    BLACKLIST_UPDATE,
    RESET_ALL;

    private static final String SEP = ":";

    // ── encode ──────────────────────────────────────────────────────

    public static String encodeSettingsUpdate(String uuid, String field, String value) {
        return SETTINGS_UPDATE.name() + SEP + uuid + SEP + field + SEP + value;
    }

    public static String encodeBlacklistUpdate(String uuid, String action, String materials) {
        return BLACKLIST_UPDATE.name() + SEP + uuid + SEP + action + SEP + materials;
    }

    // ── decode ──────────────────────────────────────────────────────

    public static RedisMessage parseType(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf(SEP);
        String typePart = idx > 0 ? raw.substring(0, idx) : raw;
        try {
            return valueOf(typePart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the raw payload after the type prefix (everything after first ':').
     */
    public static String payload(String raw) {
        int idx = raw.indexOf(SEP);
        return idx >= 0 ? raw.substring(idx + 1) : "";
    }

    /**
     * Parse UUID from a SETTINGS_UPDATE or BLACKLIST_UPDATE payload.
     */
    public static String parseUUID(String payload) {
        // payload = uuid:field:value  or  uuid:action:materials
        int idx = payload.indexOf(SEP);
        return idx > 0 ? payload.substring(0, idx) : payload;
    }

    /**
     * Parse field name from SETTINGS_UPDATE payload.
     */
    public static String parseField(String payload) {
        // payload = uuid:field:value
        String[] parts = payload.split(SEP, 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    /**
     * Parse value from SETTINGS_UPDATE payload.
     */
    public static String parseValue(String payload) {
        // payload = uuid:field:value
        String[] parts = payload.split(SEP, 3);
        return parts.length >= 3 ? parts[2] : "";
    }

    /**
     * Parse action from BLACKLIST_UPDATE payload (ADD, REMOVE, RESET).
     */
    public static String parseAction(String payload) {
        String[] parts = payload.split(SEP, 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    /**
     * Parse materials from BLACKLIST_UPDATE payload (comma-separated).
     */
    public static String parseMaterials(String payload) {
        String[] parts = payload.split(SEP, 3);
        return parts.length >= 3 ? parts[2] : "";
    }

    // ── RESET_ALL helpers ────────────────────────────────────────────

    public static String encodeResetAll(int fingerprint) {
        return RESET_ALL.name() + SEP + fingerprint;
    }

    public static int parseFingerprint(String raw) {
        String p = payload(raw);
        try { return Integer.parseInt(p); } catch (NumberFormatException e) { return 0; }
    }
}
