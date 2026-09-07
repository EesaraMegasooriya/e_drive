import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, ChevronLeft, ChevronRight, Images, Play, X } from "lucide-react";
import "./Gallery.css";

async function request(path, signal) {
  const response = await fetch(`/api/public/galleries${path}`, { signal });
  if (!response.ok) throw new Error(response.status === 503 ? "The gallery drive is currently unavailable. Please try again later." : "We couldn’t load this gallery. Please try again.");
  return response.json();
}
const preview = (item) => item.contentUrl.replace(/\/content$/, "/thumbnail");

function Thumbnail({ item }) {
  const [failed, setFailed] = useState(false);
  return item && !failed ? <img src={preview(item)} alt="" loading="lazy" onError={() => setFailed(true)} /> : <span className="eg-placeholder"><Images size={34} /><span>{item ? "Preview unavailable" : "No photos yet"}</span></span>;
}

function AlbumCard({ album, index }) {
  const [cover, setCover] = useState(null);
  const [count, setCount] = useState(null);
  useEffect(() => {
    const controller = new AbortController();
    request(`/${album.id}/media?limit=1`, controller.signal).then(page => { setCover(page.items[0]); setCount(page.total); }).catch(() => {});
    return () => controller.abort();
  }, [album.id]);
  return <Link className="eg-album" to={`/gallery/${album.id}`} state={{ name: album.name }}>
    <div className="eg-cover"><Thumbnail item={cover} /><span className="eg-album-number">{String(index + 1).padStart(2, "0")}</span></div>
    <div className="eg-album-caption"><div><h2>{album.name}</h2><p>{count === null ? "Explore gallery" : `${count} memories`}</p></div><ArrowUpRight size={22} /></div>
  </Link>;
}

function Viewer({ items, selected, onSelect, onClose }) {
  const dialog = useRef(null);
  const item = items[selected];
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current.showModal();
    return () => { document.body.style.overflow = overflow; previous?.focus(); };
  }, []);
  function navigate(index) { setFailed(false); onSelect(index); }
  return <dialog ref={dialog} className="eg-viewer" aria-label={item.name} onCancel={onClose} onKeyDown={event => {
    if (event.target.tagName === "VIDEO") return;
    if (event.key === "ArrowLeft" && selected > 0) navigate(selected - 1);
    if (event.key === "ArrowRight" && selected < items.length - 1) navigate(selected + 1);
  }}>
    <div className="eg-viewer-top"><span>{item.name}</span><button autoFocus onClick={onClose} aria-label="Close viewer"><X /></button></div>
    <div className="eg-viewer-media">
      {failed ? <p>This format cannot be displayed in your browser. <a href={item.contentUrl} target="_blank" rel="noreferrer">Open original</a></p> : item.type === "video" ? <video key={item.id} src={item.contentUrl} controls playsInline autoPlay onError={() => setFailed(true)} /> : <img key={item.id} src={item.contentUrl} alt={item.name} onError={() => setFailed(true)} />}
    </div>
    <div className="eg-viewer-bottom"><button disabled={selected === 0} onClick={() => navigate(selected - 1)} aria-label="Previous media"><ChevronLeft /></button><span>{selected + 1} / {items.length}</span><a href={item.contentUrl} target="_blank" rel="noreferrer">Open original ↗</a><button disabled={selected === items.length - 1} onClick={() => navigate(selected + 1)} aria-label="Next media"><ChevronRight /></button></div>
  </dialog>;
}

export default function Gallery() {
  const { galleryId } = useParams();
  return <GalleryPage key={galleryId || "home"} galleryId={galleryId} />;
}

function GalleryPage({ galleryId }) {
  const [albums, setAlbums] = useState([]);
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [moreLoading, setMoreLoading] = useState(false);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState(null);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    const controller = new AbortController();
    Promise.all([request("", controller.signal), galleryId ? request(`/${galleryId}/media?limit=60`, controller.signal) : Promise.resolve(null)])
      .then(([list, page]) => { setAlbums(list); if (page) { setItems(page.items); setTotal(page.total); } setError(""); })
      .catch(e => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => { alive.current = false; controller.abort(); };
  }, [galleryId, attempt]);
  async function loadMore() {
    setMoreLoading(true);
    try {
      const page = await request(`/${galleryId}/media?offset=${items.length}&limit=60`);
      if (alive.current) { setItems(previous => [...previous, ...page.items.filter(item => !previous.some(existing => existing.id === item.id))]); setTotal(page.total); setError(""); }
    } catch (e) { if (alive.current) setError(e.message); }
    finally { if (alive.current) setMoreLoading(false); }
  }
  const name = albums.find(album => album.id === galleryId)?.name || "Gallery";
  return <div className="eg-shell">
    <header className="eg-header"><Link to="/" className="eg-brand"><span className="eg-brand-mark">e.</span> GALLERY</Link><span className="eg-header-note">A little collection of life.</span></header>
    <main className="eg-main">
      {galleryId && <Link className="eg-back" to="/"><ArrowLeft size={16} /> All galleries</Link>}
      <section className="eg-intro"><p className="eg-eyebrow">{galleryId ? "THE COLLECTION" : "PLACES. PEOPLE. MOMENTS."}</p><h1>{galleryId ? name : <>Good times.<br /><em>Kept here.</em></>}</h1><p className="eg-description">{galleryId ? `${total} photos & videos to look back on.` : "The trips, the milestones, and everything in between."}</p></section>
      {error && <div className="eg-message" role="alert"><p>{error}</p><button onClick={() => { setLoading(true); setAttempt(a => a + 1); }}>Try again</button></div>}
      {loading ? <div className="eg-message" role="status">Gathering your memories…</div> : <>
        {!galleryId ? <><div className="eg-section-label"><h2>Your galleries</h2><span>{albums.length} collections</span></div><div className="eg-albums">{albums.map((album, index) => <AlbumCard key={album.id} album={album} index={index} />)}</div>{!albums.length && !error && <p className="eg-message">Your first collection will appear here soon.</p>}</> : <><div className="eg-media-grid">{items.map((item, index) => <button className="eg-media" key={item.id} aria-label={`Open ${item.name}`} onClick={() => setSelected(index)}><Thumbnail item={item} />{item.type === "video" && <span className="eg-play"><Play size={18} fill="currentColor" /></span>}<span className="eg-media-name">{item.name}</span></button>)}</div>{!items.length && !error && <p className="eg-message">No photos or videos in this collection yet.</p>}{items.length < total && <button className="eg-load" disabled={moreLoading} onClick={loadMore}>{moreLoading ? "Loading…" : "Load more memories"}</button>}</>}
      </>}
    </main><footer className="eg-footer"><span>E Gallery</span><span>Made for remembering.</span></footer>
    {selected !== null && <Viewer items={items} selected={selected} onSelect={setSelected} onClose={() => setSelected(null)} />}
  </div>;
}
