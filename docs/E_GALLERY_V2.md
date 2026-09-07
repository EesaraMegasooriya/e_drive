# E Gallery V2 backend

Each visible top-level folder in `/mnt/e-gallery` becomes a public gallery.
Direct photos/videos are listed from disk on each request; nested directories,
symlinks, hidden files, and unsupported extensions are excluded. All discovered
media is publicly accessible. No upload or database import is required.

## Server setup

Verify the external disk is mounted with UUID `6CAC-54E8`:

```sh
findmnt --mountpoint /mnt/e-gallery -o SOURCE,UUID,TARGET
```

Only after confirming that UUID, create the identity file as `eesara`:

```sh
printf '%s\n' '6CAC-54E8' > /mnt/e-gallery/.e-gallery-volume
```

The backend returns 503 `GALLERY_STORAGE_UNAVAILABLE` if the marker is missing
or incorrect. It never creates the root. `nofail` alone does not detect disk
availability for the application. Recreate the backend after reconnecting and
remounting the disk to refresh Docker's bind mount.

Compose mounts the gallery read-only, separately from existing upload storage.
Container read permissions must be verified on the server. Existing MySQL/auth
infrastructure is still required.

```sh
docker compose up -d --build backend frontend
curl -i http://localhost/api/public/galleries
```

## API

- `GET /api/public/galleries`
- `GET /api/public/galleries/{galleryId}/media?offset=0&limit=100`
- `GET /api/public/galleries/{galleryId}/media/{mediaId}/content`

Use returned IDs and `contentUrl` values. IDs encode filenames as URL-safe
Base64 and change when renamed. Media pages include items, offset, limit, total;
items include name, MIME type, image/video type, size and modification time in
epoch milliseconds. Limit: 1–200. Direct media paths are sorted lexically before
pagination; a persistent index may be needed for very large galleries.

Content uses filesystem resources for HTTP byte ranges/video seeking, with
cache revalidation. Supported extensions: JPG, JPEG, PNG, WebP, GIF, AVIF, HEIC,
HEIF, MP4, MOV, WebM, M4V, MKV. Browser codec support varies; this milestone does
not transcode media. Thumbnails and React gallery UI are the next milestone.

## Gallery interface and thumbnails

The public homepage now shows album cards. `/gallery/{galleryId}` displays a
responsive media grid with pages of 60 items and a modal photo/video viewer.
The viewer supports previous/next, arrow keys, Escape, and opening originals.
The frontend has no login or account screens. Legacy URLs such as `/drive`
and `/login` redirect to the public gallery homepage. The global auth provider
is removed, so old session expiry cannot redirect gallery visitors. Existing
backend administration and write APIs retain their authentication requirements.

`GET /api/public/galleries/{galleryId}/media/{mediaId}/thumbnail` generates a
640px JPEG preview using FFmpeg (already installed in the Docker image).
Previews are cached under `${STORAGE_LOCATION}/.gallery-thumbnails`, separate
from the read-only gallery disk. Set `GALLERY_THUMBNAIL_CACHE` to override it.
Source path, size, and modification time determine cache identity. Old cache
versions are not automatically removed yet. Two conversions may run at once;
image conversion is limited to 60 seconds per stage and video conversion to 10 minutes. Failed previews show a placeholder in the UI.
Local development requires FFmpeg on PATH or `GALLERY_FFMPEG` configured.
Video thumbnails use the first frame.

HEIC/HEIF photos use `heif-convert` (Docker package `libheif-examples`) before
FFmpeg generates JPEG thumbnails and a 2560px viewer image. MOV files use a
cached H.264/AAC MP4 for browser playback. Other videos attempt original playback
and fall back to conversion if the browser rejects them. The `/browser` media
endpoint serves these converted files with range support. Originals remain
available through `/content`. Conversion is on demand; the first request waits
for it to finish. Failed or busy conversions can be retried in the viewer.
Actual camera-file decoding must be verified on the deployed container;
HDR tone mapping and every HEIF variant are not guaranteed.
