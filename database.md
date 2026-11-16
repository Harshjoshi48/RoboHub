# NeuroGrip / NeuroPulse — Cloud Firestore Database Architecture & Admin Management Guide

## 1. Database Overview
- **Primary Server & Cloud Database**: **Firebase Cloud Firestore**
  - **Firebase Project ID**: `gen-lang-client-0929832946`
  - **Named Firestore Database ID**: `ai-studio-android-neuropul-c40fab35-e98a-46c6-850b-96b0e10b6910` (configured in `app/src/main/res/values/firebase_applet_config.xml`)
- **PostgreSQL & Server SQLite Removal**:
  - `psycopg2-binary` has been removed from `backend/requirements.txt`.
  - `DATABASE_URL` and server-side SQLite/PostgreSQL dependencies have been removed from `backend/main.py` and `.env.example`.
  - All backend persistence and Android Admin Database Management operations run through Cloud Firestore (`FirestoreDatabaseRepository.kt` and `backend/main.py`).

---

## 2. Explicit Collection Allowlist & Permissions

| Collection Path | Display Name | Create | Update | Delete | Required View Permission | Required Write Permission |
| :--- | :--- | :---: | :---: | :---: | :--- | :--- |
| `users` | Users & Admins | Yes | Yes | Yes | `VIEW_USERS` | `EDIT_USERS` / `DELETE_USERS` |
| `remote_config` | Remote App Config | Yes | Yes | No | `VIEW_DASHBOARD` | `MANAGE_REMOTE_CONFIG` |
| `notifications` | Push Broadcasts | Yes | Yes | Yes | `VIEW_DASHBOARD` | `SEND_NOTIFICATIONS` |
| `banners_content` | CMS Banners | Yes | Yes | Yes | `VIEW_DASHBOARD` | `MANAGE_BANNERS` |
| `support_tickets` | Support Tickets | Yes | Yes | Yes | `VIEW_DASHBOARD` | `MANAGE_SUPPORT_TICKETS` |
| `subscriptions` | Subscriptions | Yes | Yes | Yes | `VIEW_DASHBOARD` | `MANAGE_SUBSCRIPTIONS` |
| `config_history` | Config History | Yes | No | Yes | `VIEW_DASHBOARD` | `MANAGE_REMOTE_CONFIG` |
| `backups` | Backups | Yes | Yes | Yes | `VIEW_DASHBOARD` | `MANAGE_BACKUPS` |
| `audit_logs` | Audit Logs (Append-Only) | Yes | **No** | **No** | `VIEW_AUDIT_LOGS` | `VIEW_AUDIT_LOGS` |

### Protected System Collections (Strictly Blocked in UI, Backend, and Security Rules)
- `admins` (read-only in rules, never client-writable)
- `system_credentials`
- `service_accounts`
- `firebase_private_keys`
- `auth_tokens`

---

## 3. Security Rules & Testing
- **Zero-Trust Security Rules (`firestore.rules`)**:
  - Enforces strict schema validation (`hasOnlyAllowedFields`), string length bounds, numeric bounds, nested Map/List bounds, immutable ownership (`userId`), anti-privilege-escalation (`role == 'USER'` on self-registration), and server timestamp checks (`request.time`).
- **Two-Tier Automated Verification**:
  1. **Tier 1 (JavaScript Emulator Suite)**: `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
  2. **Tier 2 (Android Robolectric + Kotlin SDK Suite)**: `gradle :app:testDebugUnitTest --tests '*Rule*'`
  3. **Python Backend Firestore API Suite**: `python3 -m unittest -v backend/test_backend.py`
