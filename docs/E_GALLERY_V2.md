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

Compose mounts the gallery read-write for authenticated admin operations, separately from existing upload storage.
Container read permissions must be verified on the server. Existing MySQL infrastructure is still required.

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

## Gallery interface

The public homepage shows album cards. Albums have pages of 60 items and a
photo/video viewer with keyboard navigation. Legacy Drive URLs redirect to the public homepage. /admin has its own login. Backend write/admin APIs remain protected.

The grid loads lazy 640px JPEG previews; the viewer and photo slideshow use
1920px previews. Original files are available separately, and video playback
serves the original without transcoding. FFmpeg (already in the backend Docker
image) generates only still previews, including video poster frames. Originals
are never modified. If a photo thumbnail cannot be generated, the grid falls
back to the original image.

Preview cache defaults to `${storage.location}/.gallery-previews` and can be
overridden with the Spring property `gallery.preview-cache`. The cache lives on
the backend upload volume, not the external drive. FFmpeg can be configured with
`gallery.ffmpeg`. Two workers generate previews, with a 45-second queue wait and
45-second processing timeout. Cached JPEGs older than 30 days are removed daily.
A replaced source gets a new cache key based on path, size and modification time.

`GET /api/public/galleries/{galleryId}/media/{mediaId}/preview?size=640&v=SIZE-MTIME`
accepts only 640 or 1920. Browser caches revalidate using ETags, allowing the backend to deny newly hidden
items before returning 304. Preview files remain cached on disk. Changes preserving both size and modification time require
manual cache invalidation. First uncached visits still need disk reads and
preview generation; subsequent visits reuse disk and browser caches.

System folders are excluded from listings and direct gallery access:
dot-prefixed folders, dollar-prefixed folders (including $RECYCLE.BIN),
System Volume Information, RECYCLER, RECYCLED and lost+found.

The slideshow covers all photo pages (videos are excluded), supports pause/play,
3/5/8/12-second intervals, looping, previous/next, and fullscreen where supported.
The next slide is preloaded. Choose an audio file from your device for looping
music; the browser uses a local object URL and does not upload the music.
Music pauses with the slideshow and stops on close. Browser audio permissions
may require using the audio player's play button.

A 503 GALLERY_STORAGE_UNAVAILABLE is independent of media formats: verify the
host mount, the .e-gallery-volume marker, and the backend container bind mount.

## Deploy these updates

From the Mac project root:

```sh
rsync -av --exclude node_modules --exclude dist frontend/ eesara@eesara-server:/opt/e-drive/frontend/
rsync -av --delete drive/src/main/java/com/eesara/drive/gallery/ eesara@eesara-server:/opt/e-drive/drive/src/main/java/com/eesara/drive/gallery/
```

On the server:

```sh
cd /opt/e-drive
docker compose up -d --build backend frontend
docker compose restart frontend
```

The original gallery mount and identity file must already be configured.

## Gallery administration

Open `/admin` to sign in with the requested `GallaryAdmin` account and the
password supplied during setup. The password is stored as a salted PBKDF2 hash
on the backend, never in the frontend. Override `gallery.admin.username` and
`gallery.admin.password-hash` (salt-hex:PBKDF2-SHA256-hex, 210000 iterations,
256-bit output) when changing credentials.

Admin sessions last eight hours, are revoked on logout and server restart,
and are sent in the X-Gallery-Token header. Browser storage is scoped to the
current tab. The dedicated security chain protects every gallery-admin route
except POST /api/gallery-admin/login. Sign-in is limited to 20 attempts per
five minutes per backend instance. Admin tokens cannot authorize legacy Drive
admin APIs. Public browsing remains anonymous.

Features: list hidden and visible folders, create/rename/hide/show/delete folders,
browse paginated media, authenticated previews, rename/hide/show/delete media,
choose/reset a cover photo, and upload multiple photos/videos sequentially.
Uploads have a 512 MB per-file limit, basic file-signature validation, and never
overwrite an existing filename. Errors identify the file that failed.

Settings live in each album's hidden `.e-gallery.properties` file and survive
folder renaming and server restarts. Hidden folders/files are denied on public
listing, content, cover, and preview endpoints. Existing copies already downloaded
or cached under older deployments cannot be retracted. When upgrading from the
previous immutable-cache deployment, purge any upstream cache if immediate
revocation of old cached public URLs is needed.

Delete moves items into `/mnt/e-gallery/.e-gallery-trash/` with unique names;
it does not permanently erase them or free drive space. Restore manually by
moving an item back into its album (remove the UUID prefix). Hidden metadata and
system folders are excluded from public and admin album discovery.

### Enable writes on the server

The backend needs a writable bind mount for administration. For the short
volume syntax previously installed, change:

```yaml
- /mnt/e-gallery:/mnt/e-gallery:ro
```

to:

```yaml
- /mnt/e-gallery:/mnt/e-gallery:rw
```

For long syntax set `read_only: false`. Keep the other volumes and database
settings intact. Transfer the frontend and complete gallery Java directory
using the deployment commands above, then rebuild/recreate both services.
The gallery directory includes the admin security configuration, so no changes
to the legacy SecurityConfig are needed. The mounted disk must still have the
correct identity file and write permissions.
