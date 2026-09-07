import { useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Maximize, Music, Pause, Play, X } from "lucide-react";
import { originalUrl, previewUrl } from "./galleryMedia";

export default function GallerySlideshow({ galleryId, onClose }) {
  const dialog = useRef(null);
  const audio = useRef(null);
  const musicUrl = useRef(null);
  const [photos, setPhotos] = useState([]);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [seconds, setSeconds] = useState(5);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [readyUrl, setReadyUrl] = useState("");
  const [failedUrl, setFailedUrl] = useState("");
  const [musicName, setMusicName] = useState("");
  const [musicError, setMusicError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const item = photos[index];
  const url = item ? previewUrl(item, 1920) : "";
  const failed = failedUrl === url;

  useEffect(() => {
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current.showModal();
    return () => {
      document.body.style.overflow = overflow;
      if (musicUrl.current) URL.revokeObjectURL(musicUrl.current);
      previous?.focus();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const all = [];
        let offset = 0;
        let total = Infinity;
        while (offset < total) {
          const response = await fetch(`/api/public/galleries/${galleryId}/media?offset=${offset}&limit=200`, { signal: controller.signal });
          if (!response.ok) throw new Error("Couldn’t load the slideshow. Please try again.");
          const page = await response.json();
          total = page.total;
          if (!page.items.length) break;
          offset += page.items.length;
          all.push(...page.items.filter(media => media.type === "image"));
        }
        if (!controller.signal.aborted) {
          setPhotos(all); setIndex(0); setLoaded(true); setError("");
        }
      } catch (e) { if (!controller.signal.aborted) { setError(e.message); setLoaded(true); } }
    }
    load();
    return () => controller.abort();
  }, [galleryId, attempt]);

  useEffect(() => {
    if (!playing || photos.length < 2 || (readyUrl !== url && !failed)) return;
    const timer = window.setTimeout(() => setIndex(i => (i + 1) % photos.length), seconds * 1000);
    return () => window.clearTimeout(timer);
  }, [playing, photos.length, readyUrl, url, failed, seconds]);

  useEffect(() => {
    if (!photos.length) return;
    const next = new Image();
    next.src = previewUrl(photos[(index + 1) % photos.length], 1920);
    return () => { next.src = ""; };
  }, [photos, index]);

  useEffect(() => {
    if (!audio.current || !musicUrl.current) return;
    if (playing && loaded && photos.length && !error) {
      audio.current.play().catch(() => setMusicError("Press play on the audio player to enable music."));
    } else audio.current.pause();
  }, [playing, loaded, photos.length, error]);

  function pickMusic(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    audio.current.pause();
    if (musicUrl.current) URL.revokeObjectURL(musicUrl.current);
    musicUrl.current = URL.createObjectURL(file);
    audio.current.src = musicUrl.current;
    audio.current.volume = 0.4;
    setMusicName(file.name);
    setMusicError("");
    if (playing) audio.current.play().catch(() => setMusicError("Use the audio player to start music."));
  }
  function move(delta) { setIndex(i => (i + delta + photos.length) % photos.length); }

  return <dialog ref={dialog} className="eg-viewer eg-slideshow" aria-label="Photo slideshow" onCancel={onClose} onKeyDown={event => {
    if (["INPUT", "SELECT", "AUDIO", "BUTTON"].includes(event.target.tagName)) return;
    if (event.key === "ArrowRight" && photos.length) move(1);
    if (event.key === "ArrowLeft" && photos.length) move(-1);
    if (event.code === "Space") { event.preventDefault(); setPlaying(p => !p); }
  }}>
    <div className="eg-viewer-top"><span>{item?.name || "Slideshow"}</span><div className="eg-slide-actions">
      <button aria-label="Toggle fullscreen" onClick={() => {
        const action = document.fullscreenElement ? document.exitFullscreen?.() : dialog.current.requestFullscreen?.();
        action?.catch(() => {});
      }}><Maximize size={20} /></button>
      <button autoFocus aria-label="Close slideshow" onClick={onClose}><X /></button>
    </div></div>
    <div className="eg-viewer-media">
      {!loaded && <p role="status">Loading your slideshow…</p>}
      {error && <p role="alert">{error} <button onClick={() => { setLoaded(false); setAttempt(a => a + 1); }}>Try again</button></p>}
      {loaded && !error && !photos.length && <p>No photos in this gallery yet.</p>}
      {item && !error && <>
        {readyUrl !== url && !failed && <p className="eg-preparing" role="status">Loading photo…</p>}
        {failed ? <p>Preview unavailable. <a href={originalUrl(item)} target="_blank" rel="noreferrer">Open original</a></p> :
          <img key={url} src={url} alt={item.name} decoding="async" onLoad={() => setReadyUrl(url)} onError={() => setFailedUrl(url)} />}
      </>}
    </div>
    <div className="eg-slideshow-controls">
      <button disabled={!photos.length} onClick={() => move(-1)} aria-label="Previous photo"><ChevronLeft /></button>
      <button disabled={!photos.length} onClick={() => setPlaying(p => !p)} aria-label={playing ? "Pause slideshow" : "Play slideshow"}>{playing ? <Pause /> : <Play />}</button>
      <button disabled={!photos.length} onClick={() => move(1)} aria-label="Next photo"><ChevronRight /></button>
      <span>{photos.length ? index + 1 : 0} / {photos.length}</span>
      <label>Interval <select value={seconds} onChange={e => setSeconds(Number(e.target.value))}><option value="3">3 sec</option><option value="5">5 sec</option><option value="8">8 sec</option><option value="12">12 sec</option></select></label>
      <label className="eg-music-picker"><Music size={17} /> Choose music<input type="file" accept="audio/*" onChange={pickMusic} /></label>
    </div>
    <div className="eg-music-controls">
      <span>{musicName || "Choose a song from your device. It stays on your device."}</span>
      <audio ref={audio} controls loop hidden={!musicName} onError={() => setMusicError("This audio file cannot be played. Choose another song.")} />
      {musicError && <span role="status">{musicError}</span>}
    </div>
  </dialog>;
}
