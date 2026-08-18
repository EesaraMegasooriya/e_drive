#!/bin/sh
set -eu

: "${DRIVE_ADMIN_JWT:?Set DRIVE_ADMIN_JWT to an administrator JWT}"
: "${DRIVE_OWNER_JWT:?Set DRIVE_OWNER_JWT to the selected owner's JWT}"
: "${DRIVE_OWNER_UUID:?Set DRIVE_OWNER_UUID to the E Drive owner UUID}"
DRIVE_API_URL="${DRIVE_API_URL:-https://drive.eesara.com/api}"
OUTPUT_FILE="${OUTPUT_FILE:-.env.wishes.local}"

auth="Authorization: Bearer ${DRIVE_ADMIN_JWT}"
owner_auth="Authorization: Bearer ${DRIVE_OWNER_JWT}"
create_folder() {
  name="$1"
  parent="$2"
  if [ -n "$parent" ]; then body="{\"name\":\"$name\",\"parentUuid\":\"$parent\"}"; else body="{\"name\":\"$name\"}"; fi
  curl --fail --silent --show-error -H "$owner_auth" -H "Content-Type: application/json" -d "$body" "$DRIVE_API_URL/folders" | jq -r .uuid
}

applications_uuid="$(create_folder Applications "")"
wishes_uuid="$(create_folder Wishes "$applications_uuid")"
production_uuid="$(create_folder production "$wishes_uuid")"
create_folder development "$wishes_uuid" >/dev/null

payload="$(jq -n --arg owner "$DRIVE_OWNER_UUID" --arg folder "$production_uuid" '{name:"Wishes Production",userUuid:$owner,folderUuid:$folder,scopes:["files:upload","files:read","files:update","files:delete","folders:read"]}')"
created="$(curl --fail --silent --show-error -H "$auth" -H "Content-Type: application/json" -d "$payload" "$DRIVE_API_URL/admin/api-keys")"
key="$(printf '%s' "$created" | jq -r .key)"
umask 077
printf 'STORAGE_PROVIDER=drive\nDRIVE_API_URL=%s\nDRIVE_API_KEY=%s\nDRIVE_PUBLIC_BASE_URL=https://drive.eesara.com\nDRIVE_FOLDER_UUID=%s\nMAX_IMAGE_SIZE_MB=10\n' "$DRIVE_API_URL" "$key" "$production_uuid" > "$OUTPUT_FILE"
chmod 600 "$OUTPUT_FILE"
printf 'Wishes credentials written to %s (mode 600).\n' "$OUTPUT_FILE"
