# Mluona IPTV Backend & Subscription System (نظام الاشتراك والموقع)

Backend API, PostgreSQL schema, and Admin Dashboard built for **Mluona IPTV**.

## Architecture & Database Tables (المخطط وقواعد البيانات)
- **`users`**: User records, emails, password hashes, `trial_used` boolean flag.
- **`plans`**: Subscription plans (`trial_7d` 7 days, `monthly` 30 days, `yearly` 365 days).
- **`subscriptions`**: Source of truth for subscription status (`trial`, `active`, `expired`, `suspended`), `expires_at`, `started_at`.
- **`devices`**: Registered devices per user, tracking `device_hash` (`ANDROID_ID` hashed with `SERVER_SALT`) to prevent trial abuse.
- **`device_codes`**: 6-character TV pairing codes and pairing statuses (`PENDING`, `LINKED`, `EXPIRED`).
- **`playlists`**: Server-managed IPTV playlists with AES-256 encrypted passwords and soft-delete (`deleted_at`).
- **`activation_codes`**: Batch-generated activation codes for instant redemption (`POST /v1/redeem`).
- **`payments`**: Payment and transaction logs (activation codes, bank transfers, Google Play payments).
- **`audit_log`**: Administrator audit trail recording modifications.

## Environment Variables
```env
PORT=3000
DATABASE_URL=postgres://user:password@localhost:5432/mluona_iptv
JWT_SECRET=mluona_super_secret_jwt_key_2026
AES_SECRET_KEY=mluona_aes_256_encryption_key_32b
SERVER_SALT=mluona_tv_salt_xyz999
```

## API Endpoints (نقاط الاتصال)
### 1. Device Pairing (تسجيل الدخول على التلفاز بالكود)
- `POST /v1/device/code`: Generates a 6-character code (e.g. `ML8924`) and QR payload URL.
- `POST /v1/device/poll`: Polls every 3 seconds until token is acquired.
- `POST /v1/device/link`: Used by subscriber from smartphone/web to pair the 6-character TV code.

### 2. Authentication & 7-Day Trial (التجربة المجانية 7 أيام)
- `POST /v1/auth/register`: Automatically provisions a 7-day trial subscription with `trial_used = true` and `device_hash` validation.
- `POST /v1/auth/login`: Standard email/password login.

### 3. Subscription Gatekeeper (بوابة الاشتراك)
- `GET /v1/me/subscription`: Returns subscription status (`trial`, `active`, `expired`, `suspended`), `expiresAt`, `serverTime`, and signed JWT token (valid for 48-72h offline grace).

### 4. Server-Driven Playlists (القوائم تأتي من السيرفر)
- `GET /v1/playlists`: Returns playlists sorted by `sort_order` with AES-256 decrypted Xtream credentials and ETag caching.

### 5. Activation Codes (أكواد التفعيل)
- `POST /v1/redeem`: Redeems activation code, activating or extending subscription.

### 6. Admin Panel (لوحة التحكم)
- `GET /admin`: Complete Admin Web GUI.
- `GET /pair`: Smartphone pairing web page.
- `GET /v1/admin/stats`: Statistics overview.
- `GET /v1/admin/playlists` / `POST` / `DELETE` / `POST .../restore`: Playlist CRUD with soft delete.
- `POST /v1/admin/playlists/test`: Live Xtream server test via `player_api.php`.
- `POST /v1/admin/activation-codes/batch`: Batch activation code generator.
- `PUT /v1/admin/subscribers/:id`: Extend subscription, suspend/resume, reset device count.
- `GET /v1/admin/audit-logs`: Audit logs.
