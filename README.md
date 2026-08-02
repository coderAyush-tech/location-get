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

## Read-only admin API

The admin terminal uses a BCrypt-protected login and a signed 15-minute Bearer
token. Admin credentials and signing secrets exist only in the backend
environment.

```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{"username":"...","password":"..."}
```

Use the returned token for both protected endpoints:

```http
GET /api/v1/admin/captures?page=0&size=20&sort=createdAt,desc&query=&locationSource=ALL
Authorization: Bearer <accessToken>

GET /api/v1/admin/captures/{captureId}/photo
Authorization: Bearer <accessToken>
```

`locationSource` accepts `ALL`, `GPS`, `GEO_IP`, and `RAW_IP`. The list is
always newest-first and uses MongoDB pagination. Photo bytes are returned only
through the protected endpoint with `Cache-Control: no-store, private`; no
public image URL is created.

The existing capture documents do not store image dimensions or user-agent
values, so those optional admin fields are returned as `null`. For Geo-IP
captures, city/region/country are derived from the existing combined `address`
value without changing the public capture workflow or stored document schema.

## Render and MongoDB keep-alive

The protected endpoint below performs a MongoDB `{ ping: 1 }` command. It does
not create dummy photos, locations, or database documents.

```http
GET /api/health/keep-alive
X-Keep-Alive-Token: <KEEP_ALIVE_TOKEN>
```

Success returns:

```json
{
  "backend": "up",
  "mongodb": "up",
  "checkedAt": "2026-07-30T10:00:00Z"
}
```

The repository includes
[`.github/workflows/keep-alive.yml`](.github/workflows/keep-alive.yml), which
calls this endpoint every ten minutes. Configure the same randomly generated
value in both locations:

1. Render environment variable: `KEEP_ALIVE_TOKEN`
2. GitHub repository `Settings > Secrets and variables > Actions > Secrets`:
   `KEEP_ALIVE_TOKEN`

Never put this token in frontend or Netlify environment variables. The workflow
can also be run manually from the GitHub Actions page to verify the setup.

## Required production environment

- `MONGODB_URI`
- `MONGODB_DATABASE`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `STORE_LOCATIONS=true`
- `KEEP_ALIVE_TOKEN`
- `LOCATIONIQ_TOKEN` (optional; only needed for GPS reverse geocoding through
  the existing `/api/location` endpoint)
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD_HASH` (BCrypt hash only, never a plain password)
- `ADMIN_JWT_SECRET` (at least 32 random bytes)
- `ADMIN_TOKEN_TTL_SECONDS=900`

Optional admin hardening values:

- `ADMIN_LOGIN_MAX_FAILURES=5`
- `ADMIN_LOGIN_WINDOW_SECONDS=900`
- `TRUSTED_PROXY_CIDRS` (defaults to loopback/private/link-local/CGNAT proxy
  networks suitable for the Render proxy path)

Set `CORS_ALLOWED_ORIGIN_PATTERNS` to the exact comma-separated values
`https://bestue.netlify.app,http://localhost:5173`. Do not place any admin
credential, password hash, or JWT secret in Netlify/frontend variables.

See [.env.example](.env.example) for upload limit settings.
