# Camera capture backend

Spring Boot backend for a simple consent-based camera flow. The browser captures
a photo, requests location permission at the same time, and uploads the photo
plus any available coordinates. The backend stores the original image bytes and
location metadata directly in MongoDB.

Cloudinary and Gemini are not used.

## Camera upload API

```http
POST /api/v1/captures
Content-Type: multipart/form-data

photo=<required JPEG, PNG, or WebP file>
latitude=<optional number>
longitude=<optional number>
accuracy=<optional non-negative number>
```

`latitude` and `longitude` must be sent together. Do not manually set the
multipart `Content-Type` header in frontend `fetch`; the browser adds its
boundary automatically.

Success returns `201 Created`:

```json
{
  "id": "mongodb-document-id",
  "saved": true,
  "contentType": "image/jpeg",
  "sizeBytes": 123456,
  "latitude": 28.6139,
  "longitude": 77.209,
  "accuracy": 18.4,
  "locationSource": "gps",
  "address": null,
  "clientIp": "203.0.113.5",
  "savedAt": "2026-07-29T10:00:00Z"
}
```

The MongoDB collection is `captured_photos`. Its `photo` field contains the
binary image. If GPS is denied or unavailable, the backend attempts Geo-IP and
sets `locationSource` to `ip`. If providers are unavailable, the photo is still
saved with `locationSource: "raw_ip"` and the visitor's raw public IP.

The browser must show a clear disclosure before requesting camera/location
permission and uploading. Camera and geolocation are secure-context APIs, so
production must use HTTPS.

## Existing location APIs

- `POST /api/location`
- `POST /api/location/fallback`

## Required production environment

- `MONGODB_URI`
- `MONGODB_DATABASE`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `STORE_LOCATIONS=true`
- `LOCATIONIQ_TOKEN` (optional; only needed for GPS reverse geocoding through
  the existing `/api/location` endpoint)

See [.env.example](.env.example) for upload limit settings.
