package com.eesara.drive.storage;

public record StorageStats(long totalBytes, long availableBytes) {
    public long usedBytes() {
        return Math.max(0L, totalBytes - availableBytes);
    }
}
