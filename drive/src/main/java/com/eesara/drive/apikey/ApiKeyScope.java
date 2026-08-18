package com.eesara.drive.apikey;

public enum ApiKeyScope {
    FILES_UPLOAD("files:upload"), FILES_READ("files:read"), FILES_UPDATE("files:update"),
    FILES_DELETE("files:delete"), FOLDERS_READ("folders:read");

    private final String value;
    ApiKeyScope(String value) { this.value = value; }
    public String value() { return value; }
    public static boolean valid(String value) {
        for (ApiKeyScope scope : values()) if (scope.value.equals(value)) return true;
        return false;
    }
}
