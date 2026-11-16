# Security Specification (Phase 0: Payload-First Security TDD)

## 1. Data Invariants
1. **Default-Deny & Allowlisted Collections**: Only explicitly allowlisted collections (`admins`, `users`, `notifications`, `remote_config`, `config_history`, `banners_content`, `support_tickets`, `audit_logs`, `subscriptions`, `backups`) have access rules; all other paths (`/{document=**}`) are denied.
2. **Authentication & Ownership**: Every read and write requires `request.auth != null`. Standard users can only read/write their own user profile (`userId == request.auth.uid`) and support tickets (`userId == request.auth.uid`), and cannot self-assign privileged roles (`role` must be `'USER'` on self-creation and is immutable for non-admins).
3. **Trusted Admin Verification**: Admin status (`isAdmin()`) is verified strictly via `exists(/databases/$(database)/documents/admins/$(request.auth.uid))` or verified bootstrapped admin email (`request.auth.token.email == 'hj195344@gmail.com' && request.auth.token.email_verified == true`).
4. **Immutable Audit Trail**: Documents in `/audit_logs/{logId}` are append-only (`allow update, delete: if false;`).
5. **Temporal & Schema Integrity**: Every `create` and `update` validates `isValid[Entity](incoming())`, enforces strict key allowlists (`hasOnly` / `hasAll`), checks string/list `.size()` bounds, validates `createdAt is timestamp && createdAt <= request.time`, and enforces `incoming().createdAt == existing().createdAt` on updates.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Collection Scrape**: `GET /users` with `auth = null` -> Rejected (`PERMISSION_DENIED`).
2. **Cross-User PII Leak**: Authenticated `alice_123` executes `GET /users/bob_456` -> Rejected (`PERMISSION_DENIED`).
3. **Unfiltered User List Scrape**: Non-admin `alice_123` queries `LIST /users` without `.where("userId", "==", "alice_123")` -> Rejected (`PERMISSION_DENIED`).
4. **Privilege Escalation on Registration**: `alice_123` creates `/users/alice_123` with `{"role": "SUPER_ADMIN"}` -> Rejected (`PERMISSION_DENIED`).
5. **Self-Role Mutation (Update-Gap)**: `alice_123` updates `/users/alice_123` changing `role` from `"USER"` to `"ADMIN"` -> Rejected (`PERMISSION_DENIED`).
6. **Shadow Field Injection**: `alice_123` updates `/users/alice_123` with an undeclared field `{"isVerifiedAdmin": true}` -> Rejected via `hasOnly()`.
7. **Unverified Admin Email Spoofing**: Attacker authenticates with `email: "hj195344@gmail.com"` and `email_verified: false` to write `/remote_config/current` -> Rejected (`PERMISSION_DENIED`).
8. **Audit Log Tampering / Deletion**: Admin attempts `UPDATE` or `DELETE` on `/audit_logs/log_1` -> Rejected (`allow update, delete: if false`).
9. **Protected System Collection Access**: Admin or user attempts `GET` or `WRITE` on `/system_secrets/key_1` (not in allowlist) -> Rejected by global default-deny.
10. **Immutable Timestamp Mutation**: Owner or Admin attempts to overwrite `createdAt` during `UPDATE /users/alice_123` -> Rejected (`incoming().createdAt == existing().createdAt`).
11. **Terminal Ticket State Mutation by User**: User attempts to update `/support_tickets/t_1` after `status == 'CLOSED'` -> Rejected (`existing().status != 'CLOSED'`).
12. **Oversized String / Resource Exhaustion**: User creates support ticket with 10,000-character `subject` -> Rejected by `isValidSupportTicketDoc` size bounds (`subject.size() <= 200`).
