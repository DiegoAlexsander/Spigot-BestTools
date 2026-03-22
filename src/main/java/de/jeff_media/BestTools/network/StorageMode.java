package de.jeff_media.BestTools.network;

public enum StorageMode {
    LOCAL,
    MYSQL,
    NETWORK;

    public static StorageMode fromConfig(String value) {
        if (value == null) return LOCAL;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOCAL;
        }
    }
}
