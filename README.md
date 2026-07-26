# PhotoGenius AI backend

Spring Boot backend for the existing GPS/IP location APIs and the additive
photo-session image-enhancement flow.

## Existing APIs

These contracts remain unchanged:

- `POST /api/location`
- `POST /api/location/fallback`

## Photo-session APIs

### Direct Gemini flow (recommended)

The frontend keeps the captured original in browser memory and sends it directly
to the backend for Gemini enhancement:

```http
POST /api/v1/photo-enhancements
Content-Type: multipart/form-data

photo=<required JPEG, PNG, or WebP file>
```

Success returns `200 OK` with the enhanced binary image body and its image
`Content-Type`. Errors retain the existing JSON `ProblemDetail` contract. This
flow does not require Cloudinary and does not persist original or enhanced
image bytes. The frontend should create local object URLs for the original and
enhanced blobs, and revoke them when no longer needed.

### Legacy stored-session flow

Create a session:

```http
POST /api/v1/photo-sessions
```

Upload the original:

```http
POST /api/v1/photo-sessions/{sessionId}/photo
Content-Type: multipart/form-data

photo=<required file>
latitude=<optional number>
longitude=<optional number>
accuracy=<optional number>
```

Start asynchronous enhancement:

```http
POST /api/v1/photo-sessions/{sessionId}/enhance
```

Poll status:

```http
GET /api/v1/photo-sessions/{sessionId}
```

The enhance request returns `202 Accepted` with `PROCESSING`. Poll every two
seconds until the session becomes `COMPLETED` or `FAILED`.

## Required production environment

- `MONGODB_URI`
- `MONGODB_DATABASE`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `GEMINI_API_KEY`
- `GEMINI_IMAGE_MODEL` (defaults to `gemini-3.1-flash-image`)

Cloudinary is not required by the direct endpoint. The legacy stored-session
flow can optionally use `CLOUDINARY_CLOUD_NAME` with
`CLOUDINARY_UPLOAD_PRESET`. Alternatively, omit the preset and provide
`CLOUDINARY_API_KEY` plus `CLOUDINARY_API_SECRET` for signed uploads. When an
upload preset is configured it takes precedence, so invalid legacy credentials
do not block uploads. Without valid signed credentials, best-effort deletion of
orphaned Cloudinary assets is unavailable.

See [.env.example](.env.example) for optional limits and timeout settings.
Never expose Cloudinary or Gemini credentials in frontend code.

## Storage and AI behavior

Original and enhanced images use separate Cloudinary public IDs under:

```text
photogenius/sessions/{sessionId}/original/{uniqueId}
photogenius/sessions/{sessionId}/enhanced/{uniqueId}
```

Gemini image editing uses Google's backend-only Interactions API. The prompt is
versioned as `PHOTO_ENHANCEMENT_V1` in `ImageEnhancementPrompt`.

Official references:

- https://ai.google.dev/gemini-api/docs/image-generation
- https://cloudinary.com/documentation/java_image_and_video_upload
