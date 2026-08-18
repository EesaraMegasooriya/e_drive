# Wishes storage API

Base URL: `https://drive.eesara.com/api`

Service keys use `Authorization: Bearer edrive_...`. The plaintext secret is returned only by `POST /api/admin/api-keys`; E Drive stores its SHA-256 hash and a display prefix. All administration endpoints require an administrator JWT.

## Provisioning

Create `Applications/Wishes/production` and `development`, then create a key restricted to the production folder:

```http
POST /api/admin/api-keys
Content-Type: application/json

{
  "name": "Wishes Production",
  "userUuid": "OWNER_UUID",
  "folderUuid": "PRODUCTION_FOLDER_UUID",
  "scopes": ["files:upload", "files:read", "files:update", "files:delete", "folders:read"]
}
```

The one-time deployment helper is `scripts/bootstrap-wishes.sh`. It requires `curl`, `jq`, `DRIVE_ADMIN_JWT`, `DRIVE_OWNER_JWT`, and `DRIVE_OWNER_UUID`. It writes `.env.wishes.local` with mode `0600`; that file is ignored by Git.

## Upload

```http
POST /api/files/upload
Authorization: Bearer API_KEY
Content-Type: multipart/form-data

file=@photo.webp
folderUuid=optional-descendant-uuid
isPublic=true
```

Restricted keys default to their configured folder. Service-key uploads accept validated JPEG, PNG, and WebP images up to `WISHES_MAX_IMAGE_SIZE_MB` (10 by default). Browser/JWT uploads retain their existing behavior.

Public URLs use `https://drive.eesara.com/files/{uuid}/{storedName}` and require no authentication. They include a one-year immutable public cache policy because E Drive does not replace file content at an existing URL.

Visibility is changed with `PUT /api/files/{uuid}/visibility?isPublic=true`. Deletion uses `DELETE /api/files/{uuid}` and is idempotent for service keys: missing files return `204`.

API-key errors have `success: false`, an HTTP status, stable `code`, and human-readable `message`. Requests are limited by `SERVICE_API_KEY_REQUESTS_PER_MINUTE` (300 by default).
