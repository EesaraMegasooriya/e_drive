package com.eesara.drive.share.dto;

import java.util.List;

public record StreamPlaybackResponse(String streamUrl, List<SubtitleTrackResponse> subtitles) {
    public record SubtitleTrackResponse(String label, String language, String url) { }
}
