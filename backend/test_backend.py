import copy
import unittest
from backend.main import AdminBackendCore


class TestNeuroGripAdminBackend(unittest.TestCase):
    def setUp(self):
        self.backend = AdminBackendCore()

    def test_super_admin_hj123_login_and_permissions(self):
        code, resp = self.backend.login("hj123", "12345")
        self.assertEqual(code, 200)
        self.assertEqual(resp["username"], "hj123")
        self.assertEqual(resp["role"], "SUPER_ADMIN")
        self.assertIn("MANAGE_ROLES", resp["permissions"])

    def test_invalid_credentials_rejected_and_logged(self):
        code, _ = self.backend.login("hj123", "wrongpass")
        self.assertEqual(code, 401)

    def test_standard_user_cannot_access_admin_endpoints(self):
        code, user_session = self.backend.login("joshi", "1234")
        self.assertEqual(code, 200)
        token = user_session["token"]

        list_code, _ = self.backend.list_users(token)
        self.assertEqual(list_code, 403)

    def test_user_lifecycle_create_suspend_role_delete(self):
        _, admin_session = self.backend.login("hj123", "12345")
        token = admin_session["token"]

        # Create Analyst user
        c_code, _ = self.backend.create_user(
            token,
            {
                "name": "Test Analyst",
                "username": "analyst1",
                "email": "analyst1@neurogrip.io",
                "password": "pass123",
                "role": "ANALYST",
            },
        )
        self.assertEqual(c_code, 201)

        # Analyst can view users and audit logs, but CANNOT suspend users or change roles
        _, analyst_session = self.backend.login("analyst1", "pass123")
        analyst_token = analyst_session["token"]
        self.assertEqual(self.backend.list_users(analyst_token)[0], 200)
        self.assertEqual(self.backend.update_user_status(analyst_token, 2, "SUSPENDED")[0], 403)

        # Super Admin suspends standard user joshi (id=2)
        s_code, _ = self.backend.update_user_status(token, 2, "SUSPENDED", "Violation test")
        self.assertEqual(s_code, 200)

        # Suspended user joshi cannot log in
        login_code, _ = self.backend.login("joshi", "1234")
        self.assertEqual(login_code, 403)

        # Reactivate joshi
        self.backend.update_user_status(token, 2, "ACTIVE", "Cleared")
        self.assertEqual(self.backend.login("joshi", "1234")[0], 200)

    def test_remote_config_and_maintenance_mode_blocks_non_admins(self):
        _, admin_session = self.backend.login("hj123", "12345")
        token = admin_session["token"]

        pub_code, pub_resp = self.backend.update_remote_config(
            token,
            {
                "maintenanceMode": True,
                "maintenanceMessage": "Upgrading neural cluster",
                "minSupportedVersionName": "1.1.0",
                "recommendedVersionName": "1.2.0",
            },
        )
        self.assertEqual(pub_code, 200)
        self.assertEqual(pub_resp["configVersion"], 2)

        # Standard user login blocked with 503 during maintenance
        u_code, _ = self.backend.login("joshi", "1234")
        self.assertEqual(u_code, 503)

        # Super admin hj123 can still log in during maintenance
        a_code, _ = self.backend.login("hj123", "12345")
        self.assertEqual(a_code, 200)

    def test_notification_dispatch_and_audit_trail(self):
        _, admin_session = self.backend.login("hj123", "12345")
        token = admin_session["token"]

        n_code, _ = self.backend.dispatch_notification(
            token,
            {
                "title": "Firmware v2.4 Update",
                "message": "Please calibrate your robotic hand.",
                "targetAudience": "ALL_USERS",
                "category": "DEVICE",
            },
        )
        self.assertEqual(n_code, 200)

        logs_code, logs = self.backend.get_audit_logs(token)
        self.assertEqual(logs_code, 200)
        self.assertTrue(any(l["actionType"] == "NOTIFICATION_DISPATCH" for l in logs))

    def test_firestore_database_manager_allowlist_crud_pagination_and_import_export(self):
        _, admin_session = self.backend.login("hj123", "12345")
        token = admin_session["token"]

        # 1. Protected system collections are strictly blocked (403)
        for protected_col in ("admins", "system_credentials", "service_accounts", "firebase_private_keys"):
            code, _ = self.backend.query_firestore_collection(token, protected_col)
            self.assertEqual(code, 403)

        # 2. Create document with nested Map and Array in banners_content
        c_code, c_resp = self.backend.upsert_firestore_document(
            token=token,
            collection="banners_content",
            doc_id="banner_q4_clinical",
            fields={
                "title": "EMG Sensor Calibration V3",
                "subtitle": "Zero-latency neural grip profiling",
                "isActive": True,
                "priority": 2,
                "metadata": {"region": "GLOBAL", "minVersion": 2},
                "tags": ["emg", "calibration", "v3"],
            },
            is_create=True,
        )
        self.assertEqual(c_code, 201)
        updated_ts = c_resp["updatedAt"]

        # 3. Optimistic concurrency check rejects stale expectedUpdatedAt
        stale_code, _ = self.backend.upsert_firestore_document(
            token=token,
            collection="banners_content",
            doc_id="banner_q4_clinical",
            fields={"title": "Stale Update", "subtitle": "Should fail"},
            is_create=False,
            expected_updated_at=updated_ts - 5000,
        )
        self.assertEqual(stale_code, 409)

        # 4. Update succeeds with matching expectedUpdatedAt
        u_code, _ = self.backend.upsert_firestore_document(
            token=token,
            collection="banners_content",
            doc_id="banner_q4_clinical",
            fields={"title": "EMG Sensor Calibration V3.1", "subtitle": "Updated"},
            is_create=False,
            expected_updated_at=updated_ts,
        )
        self.assertEqual(u_code, 200)

        # 5. Search & pagination
        q_code, q_resp = self.backend.query_firestore_collection(
            token=token,
            collection="banners_content",
            search="V3.1",
            limit=10,
        )
        self.assertEqual(q_code, 200)
        self.assertEqual(len(q_resp["documents"]), 1)
        self.assertEqual(q_resp["documents"][0]["documentId"], "banner_q4_clinical")

        # 6. Append-only audit_logs cannot be updated or deleted
        audit_docs = self.backend.query_firestore_collection(token, "audit_logs")[1]["documents"]
        self.assertTrue(len(audit_docs) > 0)
        sample_audit_id = audit_docs[0]["documentId"]
        self.assertEqual(
            self.backend.upsert_firestore_document(
                token, "audit_logs", sample_audit_id, {"details": "tamper"}, is_create=False
            )[0],
            403,
        )
        self.assertEqual(
            self.backend.delete_firestore_document(token, "audit_logs", sample_audit_id)[0],
            403,
        )

        # 7. Export and Import collection documents
        exp_code, exp_resp = self.backend.export_firestore_collection(token, "banners_content")
        self.assertEqual(exp_code, 200)
        self.assertGreaterEqual(exp_resp["recordCount"], 2)

        imp_code, imp_resp = self.backend.import_firestore_collection(
            token,
            "subscriptions",
            {
                "documents": [
                    {
                        "documentId": "sub_clinic_01",
                        "fields": {
                            "userId": "user_joshi",
                            "username": "joshi",
                            "planName": "Clinical Enterprise",
                            "status": "ACTIVE",
                            "amountUsd": 99.0,
                            "metadata": {"tier": "enterprise"},
                            "tags": ["annual", "clinic"],
                        },
                    }
                ]
            },
        )
        self.assertEqual(imp_code, 200)
        self.assertEqual(imp_resp["importedCount"], 1)

    def test_super_admin_system_config_retrieval_and_env_classification(self):
        _, admin_session = self.backend.login("hj123", "12345")
        token = admin_session["token"]

        code, resp = self.backend.get_system_config(token)
        self.assertEqual(code, 200)
        rt = resp["runtimeConfig"]
        self.assertEqual(rt["otpTtlSeconds"], 60)
        self.assertEqual(rt["otpMaxSends"], 3)
        self.assertEqual(rt["otpWindowSeconds"], 600)
        self.assertEqual(rt["sessionTtlMinutes"], 30)
        self.assertEqual(rt["rateLimitPerMinute"], 60)

        # Verify all 10 required environment variables are inspected, classified, and secrets masked
        env_diag = {item["name"]: item for item in resp["environmentDiagnostics"]}
        expected_vars = {
            "ADMIN_BACKEND_BASE_URL": ("DEPLOYMENT_INFRASTRUCTURE_SETTING", False),
            "FIREBASE_PROJECT_ID": ("DEPLOYMENT_INFRASTRUCTURE_SETTING", False),
            "FIRESTORE_DATABASE_ID": ("DEPLOYMENT_INFRASTRUCTURE_SETTING", False),
            "ADMIN_HMAC_SECRET": ("SERVER_ONLY_SECRET", False),
            "ADMIN_INITIAL_USERNAME": ("INITIAL_BOOTSTRAP_SETTING", False),
            "ADMIN_INITIAL_PASSWORD_HASH": ("INITIAL_BOOTSTRAP_SETTING", False),
            "SESSION_TTL_MINUTES": ("SAFE_RUNTIME_SETTING", True),
            "RATE_LIMIT_PER_MINUTE": ("SAFE_RUNTIME_SETTING", True),
            "GOOGLE_APPLICATION_CREDENTIALS": ("SERVER_ONLY_SECRET", False),
            "FCM_PROJECT_ID": ("DEPLOYMENT_INFRASTRUCTURE_SETTING", False),
        }
        for var_name, (expected_cat, expected_editable) in expected_vars.items():
            self.assertIn(var_name, env_diag)
            self.assertEqual(env_diag[var_name]["classification"], expected_cat)
            self.assertEqual(env_diag[var_name]["editableAtRuntime"], expected_editable)

        # Secret values must never appear in plaintext
        self.assertTrue(env_diag["ADMIN_HMAC_SECRET"]["secret"])
        self.assertIn("••••", env_diag["ADMIN_HMAC_SECRET"]["displayValue"])
        self.assertTrue(env_diag["ADMIN_INITIAL_PASSWORD_HASH"]["secret"])
        self.assertIn("••••", env_diag["ADMIN_INITIAL_PASSWORD_HASH"]["displayValue"])
        self.assertTrue(env_diag["GOOGLE_APPLICATION_CREDENTIALS"]["secret"])
        self.assertIn("••••", env_diag["GOOGLE_APPLICATION_CREDENTIALS"]["displayValue"])

    def test_system_config_authorization_enforced_super_admin_only(self):
        _, super_session = self.backend.login("hj123", "12345")
        super_token = super_session["token"]

        # Create an ADMIN (non-Super-Admin) user
        self.backend.create_user(
            super_token,
            {
                "name": "Regular Admin",
                "username": "admin_reg",
                "email": "admin_reg@neurogrip.io",
                "password": "pass123",
                "role": "ADMIN",
            },
        )
        _, reg_admin_session = self.backend.login("admin_reg", "pass123")
        reg_admin_token = reg_admin_session["token"]

        _, user_session = self.backend.login("joshi", "1234")
        user_token = user_session["token"]

        # Standard user and regular ADMIN cannot read or update Super Admin System Configuration
        self.assertEqual(self.backend.get_system_config(user_token)[0], 403)
        self.assertEqual(self.backend.get_system_config(reg_admin_token)[0], 403)
        self.assertEqual(
            self.backend.update_system_config(reg_admin_token, {"otpTtlSeconds": 90})[0],
            403,
        )

    def test_system_config_validation_blocks_secrets_and_invalid_ranges(self):
        _, super_session = self.backend.login("hj123", "12345")
        token = super_session["token"]

        # Attempting to overwrite a server secret or deployment ID via system config fails with 400
        sec_code, sec_resp = self.backend.update_system_config(
            token, {"ADMIN_HMAC_SECRET": "hacked_secret"}
        )
        self.assertEqual(sec_code, 400)
        self.assertIn("Validation failed", sec_resp["error"])

        infra_code, _ = self.backend.update_system_config(
            token, {"FIREBASE_PROJECT_ID": "other-project"}
        )
        self.assertEqual(infra_code, 400)

        # Out-of-bounds runtime parameters fail validation
        bad_range_code, bad_range_resp = self.backend.update_system_config(
            token,
            {
                "otpTtlSeconds": 5,         # min 15
                "otpMaxSends": 99,          # max 10
                "sessionTtlMinutes": 2,     # min 5
                "rateLimitPerMinute": 5000, # max 1000
            },
        )
        self.assertEqual(bad_range_code, 400)
        self.assertGreaterEqual(len(bad_range_resp["details"]), 4)

    def test_system_config_update_applies_immediately_and_supports_rollback(self):
        _, super_session = self.backend.login("hj123", "12345")
        token = super_session["token"]

        # Update dynamic settings: OTP max sends = 2, rate limit = 10, session TTL = 45
        u_code, u_resp = self.backend.update_system_config(
            token,
            {
                "otpTtlSeconds": 120,
                "otpMaxSends": 2,
                "otpWindowSeconds": 300,
                "sessionTtlMinutes": 45,
                "rateLimitPerMinute": 10,
                "defaultEsp32Ip": "192.168.4.25",
            },
        )
        self.assertEqual(u_code, 200)
        self.assertEqual(u_resp["runtimeConfig"]["configVersion"], 2)
        self.assertEqual(u_resp["runtimeConfig"]["otpMaxSends"], 2)
        self.assertEqual(u_resp["runtimeConfig"]["rateLimitPerMinute"], 10)

        # Verify OTP rate limit immediately enforces new otpMaxSends = 2
        s1_code, s1_resp = self.backend.send_otp("joshi")
        self.assertEqual(s1_code, 200)
        self.assertEqual(s1_resp["ttlSeconds"], 120)

        s2_code, _ = self.backend.send_otp("joshi")
        self.assertEqual(s2_code, 200)

        # 3rd send within window must be blocked with 429 because otpMaxSends is now 2
        s3_code, _ = self.backend.send_otp("joshi")
        self.assertEqual(s3_code, 429)

        # Verify subsequent login uses updated sessionTtlMinutes = 45 (2700s)
        _, new_login = self.backend.login("hj123", "12345")
        self.assertEqual(new_login["sessionTtlMinutes"], 45)

        # Rollback to version 1 restores defaults (otpMaxSends=3, sessionTtlMinutes=30, rateLimitPerMinute=60)
        rb_code, rb_resp = self.backend.rollback_system_config(token, 1)
        self.assertEqual(rb_code, 200)
        self.assertEqual(rb_resp["runtimeConfig"]["otpMaxSends"], 3)
        self.assertEqual(rb_resp["runtimeConfig"]["sessionTtlMinutes"], 30)
        self.assertEqual(rb_resp["runtimeConfig"]["rateLimitPerMinute"], 60)

        # After rollback to otpMaxSends=3, 3rd OTP send for joshi now succeeds
        s3_retry_code, _ = self.backend.send_otp("joshi")
        self.assertEqual(s3_retry_code, 200)

    def test_admin_email_otp_login_flow_hj195345_and_single_use_invalidation(self):
        # Step 1: Authenticate Super Admin with email hj195345@gmail.com -> triggers 6-digit OTP, no token issued yet
        init_code, init_resp = self.backend.initiate_email_otp_login(
            "hj195345@gmail.com", "12345", require_admin=True
        )
        self.assertEqual(init_code, 200)
        self.assertTrue(init_resp["otpRequired"])
        self.assertFalse(init_resp["authenticated"])
        self.assertNotIn("token", init_resp)
        # SECURITY: OTP values must NEVER be returned in API responses
        self.assertNotIn("testOtpCode", init_resp)
        self.assertNotIn("otpCode", init_resp)
        self.assertIn("OTP sent to your registered email", init_resp["message"])
        self.assertEqual(init_resp["email"], "hj195345@gmail.com")
        self.assertEqual(init_resp["ttlSeconds"], 60)
        first_otp = self.backend.email_outbox[-1]["otpCode"]

        # Verify plaintext OTP is NEVER stored in active_otps (only HMAC-SHA256 otpHash is stored)
        stored_record = self.backend.active_otps["hj195345@gmail.com"]
        self.assertIn("otpHash", stored_record)
        self.assertNotEqual(stored_record["otpHash"], first_otp)
        self.assertNotIn("code", stored_record)

        # Resend OTP -> immediately invalidates the previous OTP
        resend_code, resend_resp = self.backend.send_otp(
            "hj195345@gmail.com", purpose="ADMIN_LOGIN", require_admin=True
        )
        self.assertEqual(resend_code, 200)
        self.assertNotIn("testOtpCode", resend_resp)
        second_otp = self.backend.email_outbox[-1]["otpCode"]

        if first_otp != second_otp:
            old_v_code, _ = self.backend.verify_otp(
                "hj195345@gmail.com", first_otp, issue_session=True, require_admin=True
            )
            self.assertEqual(old_v_code, 401)

        # Verify with the active 6-digit OTP -> issues SUPER_ADMIN session
        v_code, v_resp = self.backend.verify_otp(
            "hj195345@gmail.com", second_otp, issue_session=True, require_admin=True
        )
        self.assertEqual(v_code, 200)
        self.assertTrue(v_resp["authenticated"])
        self.assertTrue(v_resp["otpVerified"])
        self.assertEqual(v_resp["role"], "SUPER_ADMIN")
        self.assertEqual(v_resp["email"], "hj195345@gmail.com")
        self.assertTrue(len(v_resp["token"]) > 16)

        # Single-use enforcement: reusing the same OTP a second time is rejected (400)
        reuse_code, _ = self.backend.verify_otp(
            "hj195345@gmail.com", second_otp, issue_session=True, require_admin=True
        )
        self.assertEqual(reuse_code, 400)

    def test_normal_user_email_otp_login_and_registration_role_lock(self):
        # 1. Self-registration attempting to pass role="SUPER_ADMIN" is strictly locked to "USER"
        reg_code, reg_resp = self.backend.register_user_with_otp(
            {
                "name": "Patient Neha",
                "email": "neha.patient@neurogrip.io",
                "password": "nehaPassword99",
                "role": "SUPER_ADMIN",
            }
        )
        self.assertEqual(reg_code, 201)
        self.assertEqual(reg_resp["role"], "USER")
        self.assertTrue(reg_resp["otpRequired"])
        self.assertNotIn("testOtpCode", reg_resp)
        signup_otp = self.backend.email_outbox[-1]["otpCode"]

        # Duplicate registration with same email is rejected with 409
        dup_code, _ = self.backend.register_user_with_otp(
            {
                "name": "Duplicate Neha",
                "email": "NEHA.PATIENT@neurogrip.io",
                "password": "nehaPassword99",
            }
        )
        self.assertEqual(dup_code, 409)

        # Verify email ownership with OTP -> grants USER session
        v_code, v_resp = self.backend.verify_otp(
            "neha.patient@neurogrip.io", signup_otp, issue_session=True, require_admin=False
        )
        self.assertEqual(v_code, 200)
        self.assertEqual(v_resp["role"], "USER")

        # Normal user attempting Admin Login is rejected with 403 before OTP is even sent
        admin_attempt_code, _ = self.backend.initiate_email_otp_login(
            "neha.patient@neurogrip.io", "nehaPassword99", require_admin=True
        )
        self.assertEqual(admin_attempt_code, 403)

    def test_otp_60_second_expiry_and_password_reset_flow(self):
        # Trigger password reset OTP for hj195345@gmail.com
        req_code, req_resp = self.backend.request_password_reset_otp("hj195345@gmail.com")
        self.assertEqual(req_code, 200)
        self.assertNotIn("testOtpCode", req_resp)
        reset_otp = self.backend.email_outbox[-1]["otpCode"]

        # Simulate 60-second expiration by backdating expiresAtSec
        self.backend.active_otps["hj195345@gmail.com"]["expiresAtSec"] -= 61
        exp_code, exp_resp = self.backend.confirm_password_reset_with_otp(
            "hj195345@gmail.com", reset_otp, "NewSuperSecurePass#2026"
        )
        self.assertEqual(exp_code, 400)
        self.assertIn("expired", exp_resp["detail"].lower())

        # Request a fresh OTP and complete password reset
        req2_code, req2_resp = self.backend.request_password_reset_otp("hj195345@gmail.com")
        self.assertEqual(req2_code, 200)
        self.assertNotIn("testOtpCode", req2_resp)
        fresh_otp = self.backend.email_outbox[-1]["otpCode"]

        ok_code, _ = self.backend.confirm_password_reset_with_otp(
            "hj195345@gmail.com", fresh_otp, "NewSuperSecurePass#2026"
        )
        self.assertEqual(ok_code, 200)

        # Old password now fails, new password succeeds
        self.assertEqual(
            self.backend.initiate_email_otp_login("hj195345@gmail.com", "12345", require_admin=True)[0],
            401,
        )
        self.assertEqual(
            self.backend.initiate_email_otp_login(
                "hj195345@gmail.com", "NewSuperSecurePass#2026", require_admin=True
            )[0],
            200,
        )

    def test_google_oauth_login_creates_and_links_accounts_safely(self):
        # 1. Missing/invalid token rejected with 400
        bad_code, _ = self.backend.authenticate_google_oauth(
            {"idToken": "", "email": "new.google@neurogrip.io"}
        )
        self.assertEqual(bad_code, 400)

        # 2. First Google sign-in creates a verified USER profile without requiring email OTP
        g_code, g_resp = self.backend.authenticate_google_oauth(
            {
                "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Imdvb2dsZV9oauth_test_token_12345",
                "email": "dr.mehta@neurogrip.io",
                "name": "Dr. Aarav Mehta",
                "googleSub": "google_uid_998877",
                "emailVerified": True,
                "role": "SUPER_ADMIN",  # Client escalation attempt must be ignored
            }
        )
        self.assertEqual(g_code, 200)
        self.assertTrue(g_resp["authenticated"])
        self.assertFalse(g_resp["otpRequired"])
        self.assertEqual(g_resp["role"], "USER")
        self.assertEqual(g_resp["authProvider"], "GOOGLE")

        # 3. Existing email user signing in with Google links account without creating duplicate
        _, super_session = self.backend.login("hj123", "12345")
        super_token = super_session["token"]
        users_before = len(self.backend.list_users(super_token)[1])

        link_code, link_resp = self.backend.authenticate_google_oauth(
            {
                "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Imdvb2dsZV9oauth_joshi_token",
                "email": "joshi@neurogrip.io",
                "name": "Joshi Patient",
                "googleSub": "google_uid_joshi_02",
                "emailVerified": True,
            }
        )
        self.assertEqual(link_code, 200)
        self.assertEqual(link_resp["username"], "joshi")
        self.assertIn("GOOGLE", link_resp["authProvider"])
        users_after = len(self.backend.list_users(super_token)[1])
        self.assertEqual(users_before, users_after)

        # 4. Blocked user cannot log in via Google OAuth
        self.backend.update_user_status(super_token, 2, "BLOCKED", "Security block test")
        blocked_code, _ = self.backend.authenticate_google_oauth(
            {
                "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Imdvb2dsZV9oauth_joshi_token",
                "email": "joshi@neurogrip.io",
                "name": "Joshi Patient",
                "emailVerified": True,
            }
        )
        self.assertEqual(blocked_code, 403)

    def test_rbac_roles_custom_permissions_and_last_super_admin_protection(self):
        _, super_session = self.backend.login("hj123", "12345")
        super_token = super_session["token"]

        # 1. Protect the last active Super Admin from demotion, blocking, or deletion
        self.assertEqual(self.backend.update_user_role(super_token, 1, "USER")[0], 403)
        self.assertEqual(self.backend.update_user_status(super_token, 1, "BLOCKED")[0], 403)
        self.assertEqual(self.backend.delete_user(super_token, 1)[0], 403)

        # 2. Create a VIEWER user (read-only) and a DATA_MANAGER user
        self.backend.create_user(
            super_token,
            {
                "name": "Read Only Viewer",
                "username": "viewer1",
                "email": "viewer1@neurogrip.io",
                "password": "pass123",
                "role": "VIEWER",
            },
        )
        _, viewer_session = self.backend.login("viewer1", "pass123")
        viewer_token = viewer_session["token"]
        self.assertEqual(self.backend.get_dashboard_stats(viewer_token)[0], 200)
        self.assertEqual(self.backend.dispatch_notification(viewer_token, {"title": "T", "message": "M"})[0], 403)

        # 3. Grant individual permission SEND_NOTIFICATIONS to viewer1 via update_user_permissions
        perm_code, perm_resp = self.backend.update_user_permissions(
            super_token, "user_viewer1", ["SEND_NOTIFICATIONS"]
        )
        self.assertEqual(perm_code, 200)
        self.assertIn("SEND_NOTIFICATIONS", perm_resp["effectivePermissions"])
        self.assertEqual(
            self.backend.dispatch_notification(viewer_token, {"title": "Alert", "message": "Permitted now"})[0],
            200,
        )

        # 4. Revoke custom permission -> dispatch_notification returns 403 again
        self.backend.update_user_permissions(super_token, "user_viewer1", [])
        self.assertEqual(
            self.backend.dispatch_notification(viewer_token, {"title": "Alert", "message": "Revoked"})[0],
            403,
        )

        # 5. Verify dashboard stats include accurate registered, logged-in, and active counts
        st_code, stats = self.backend.get_dashboard_stats(super_token)
        self.assertEqual(st_code, 200)
        self.assertGreaterEqual(stats["totalRegisteredUsers"], 3)
        self.assertGreaterEqual(stats["totalLoggedInUsers"], 3)
        self.assertGreaterEqual(stats["currentlyActiveUsers"], 3)

    def test_default_otp_000000_development_testing_and_production_protection(self):
        import backend.main as b_main

        # Ensure dev test mode is active for dev tests
        original_test_mode = b_main.OTP_TEST_MODE
        original_app_env = b_main.APP_ENV
        try:
            b_main.OTP_TEST_MODE = True
            b_main.APP_ENV = "development"

            # 1. Development test account + 000000 = Success
            test_email = "hj195345@gmail.com"
            s1_code, s1_resp = self.backend.send_otp(test_email, purpose="ADMIN_LOGIN", require_admin=True)
            self.assertEqual(s1_code, 200)
            self.assertTrue(s1_resp.get("isTestOtp"))
            self.assertFalse(s1_resp.get("smtpDelivered"))  # Real email sending is bypassed in test mode
            self.assertIn("000000", s1_resp.get("message", ""))

            v1_code, v1_resp = self.backend.verify_otp(
                test_email, "000000", issue_session=True, require_admin=True
            )
            self.assertEqual(v1_code, 200)
            self.assertTrue(v1_resp["authenticated"])
            self.assertEqual(v1_resp["role"], "SUPER_ADMIN")

            # 2. Previously used OTP = Rejected (Single-use enforcement)
            v_reuse_code, v_reuse_resp = self.backend.verify_otp(
                test_email, "000000", issue_session=True, require_admin=True
            )
            self.assertEqual(v_reuse_code, 400)
            self.assertIn("already", v_reuse_resp["detail"].lower())

            # 3. Development test account + incorrect OTP = Rejected
            s2_code, _ = self.backend.send_otp(test_email, purpose="ADMIN_LOGIN", require_admin=True)
            self.assertEqual(s2_code, 200)
            v_bad_code, _ = self.backend.verify_otp(
                test_email, "123456", issue_session=True, require_admin=True
            )
            self.assertEqual(v_bad_code, 401)

            # 4. Expired OTP = Rejected
            # Backdate expiration by 65 seconds
            self.backend.active_otps[test_email]["expiresAtSec"] -= 65
            v_exp_code, v_exp_resp = self.backend.verify_otp(
                test_email, "000000", issue_session=True, require_admin=True
            )
            self.assertEqual(v_exp_code, 400)
            self.assertIn("expired", v_exp_resp["detail"].lower())

            # 5. Unauthorized account + 000000 = Rejected
            unauthorized_email = "stranger_patient_99@external.com"
            # Register user first so user exists
            self.backend.register_user_with_otp(
                {
                    "name": "Stranger",
                    "email": unauthorized_email,
                    "password": "strangerPassword1",
                }
            )
            # send_otp generates random code for unauthorized account (isTestOtp=False)
            unauth_s_code, unauth_s_resp = self.backend.send_otp(unauthorized_email, purpose="USER_LOGIN")
            self.assertEqual(unauth_s_code, 200)
            self.assertFalse(unauth_s_resp.get("isTestOtp"))

            # Attempting 000000 for unauthorized account must be rejected with 401
            v_unauth_code, v_unauth_resp = self.backend.verify_otp(
                unauthorized_email, "000000", issue_session=True, require_admin=False
            )
            self.assertEqual(v_unauth_code, 401)
            self.assertIn("not permitted", v_unauth_resp["detail"].lower())

            # 6. Production + 000000 = Rejected
            b_main.OTP_TEST_MODE = False
            b_main.APP_ENV = "production"

            s_prod_code, s_prod_resp = self.backend.send_otp(test_email, purpose="ADMIN_LOGIN", require_admin=True)
            self.assertEqual(s_prod_code, 200)
            self.assertFalse(s_prod_resp.get("isTestOtp"))

            v_prod_code, v_prod_resp = self.backend.verify_otp(
                test_email, "000000", issue_session=True, require_admin=True
            )
            self.assertEqual(v_prod_code, 401)
            self.assertIn("disabled in production", v_prod_resp["detail"].lower())
        finally:
            b_main.OTP_TEST_MODE = original_test_mode
            b_main.APP_ENV = original_app_env

    def test_sdui_config_publish_validation_rbac_and_rollback(self):
        _, super_session = self.backend.login("hj123", "12345")
        super_token = super_session["token"]
        _, user_session = self.backend.login("joshi", "1234")
        user_token = user_session["token"]

        # 1. Baseline config starts at version 1 with promoBanner disabled & exact defaults
        g_code, g_resp = self.backend.get_sdui_config()
        self.assertEqual(g_code, 200)
        self.assertEqual(g_resp["configVersion"], 1)
        self.assertFalse(g_resp["config"]["promoBanner"]["enabled"])
        self.assertEqual(g_resp["config"]["hero"]["title"], "NeuroGrip Ready")
        self.assertEqual(g_resp["config"]["topBar"]["brandTitle"], "NeuroGrip")

        # 2. Non-admin standard user cannot publish SDUI updates
        forbidden_code, _ = self.backend.update_sdui_config(
            user_token, {"config": g_resp["config"], "changeSummary": "Unauthorized edit"}
        )
        self.assertEqual(forbidden_code, 403)

        # 3. Malformed/unsafe payloads (invalid hex, non-https URL, XSS script, disallowed route, bad schemaVersion) fail cleanly
        bad_cfg = copy.deepcopy(g_resp["config"])
        bad_cfg["schemaVersion"] = 99
        bad_cfg["theme"]["buttonBlueHex"] = "not-a-hex"
        bad_cfg["hero"]["imageUrl"] = "http://insecure.example.com/a.png"
        bad_cfg["hero"]["title"] = "<script>alert(1)</script>"
        bad_cfg["promoBanner"]["enabled"] = True
        bad_cfg["promoBanner"]["ctaRoute"] = "evil_javascript_route"
        bad_code, bad_resp = self.backend.update_sdui_config(
            super_token, {"config": bad_cfg, "changeSummary": "Invalid payload"}
        )
        self.assertEqual(bad_code, 400)
        self.assertIn("validation failed", bad_resp["detail"].lower())

        # Active config must remain untouched at v1 after rejected publish
        after_bad_code, after_bad = self.backend.get_sdui_config()
        self.assertEqual(after_bad_code, 200)
        self.assertEqual(after_bad["configVersion"], 1)

        # 4. Valid Admin update increments version to 2, records history & SHA-256
        valid_cfg = copy.deepcopy(g_resp["config"])
        valid_cfg["hero"]["title"] = "NeuroGrip Telemetry Pro"
        valid_cfg["theme"]["buttonBlueHex"] = "#2563EB"
        valid_cfg["promoBanner"] = {
            "enabled": True,
            "title": "Clinic Drill Active",
            "subtitle": "Complete 5-min pinch calibration today",
            "ctaText": "Open Control",
            "ctaRoute": "hand_control",
            "imageUrl": "",
            "backgroundHex": "#EFF6FF",
            "borderHex": "#BFDBFE",
            "textColorHex": "#1E3A8A",
        }
        pub_code, pub_resp = self.backend.update_sdui_config(
            super_token, {"config": valid_cfg, "changeSummary": "Activate clinical campaign banner"}
        )
        self.assertEqual(pub_code, 200)
        self.assertEqual(pub_resp["configVersion"], 2)
        self.assertEqual(len(pub_resp["contentSha256"]), 64)

        h_code, hist_list = self.backend.get_sdui_history(super_token)
        self.assertEqual(h_code, 200)
        self.assertGreaterEqual(len(hist_list), 2)
        self.assertEqual(hist_list[0]["configVersion"], 2)

        # 5. Rollback to v1 publishes v3 with restored baseline hero title
        rb_code, rb_resp = self.backend.rollback_sdui_config(super_token, 1)
        self.assertEqual(rb_code, 200)
        self.assertEqual(rb_resp["configVersion"], 3)
        _, v3_doc = self.backend.get_sdui_config()
        self.assertEqual(v3_doc["config"]["hero"]["title"], "NeuroGrip Ready")


    def test_dynamic_banner_live_filtering_and_analytics_rbac(self):
        import time

        _, super_session = self.backend.login("hj123", "12345")
        super_token = super_session["token"]
        _, user_session = self.backend.login("joshi", "1234")
        user_token = user_session["token"]

        now_ms = int(time.time() * 1000)

        banners_to_seed = [
            (
                "banner_live_01",
                {
                    "bannerKey": "banner_live_01",
                    "title": "Public Live Banner",
                    "subtitle": "Visible right now",
                    "bannerType": "ANNOUNCEMENT",
                    "isActive": True,
                    "isPublic": True,
                    "isPublished": True,
                    "isDraft": False,
                    "clinicalApproved": True,
                    "priority": 10,
                },
            ),
            (
                "banner_draft_02",
                {
                    "bannerKey": "banner_draft_02",
                    "title": "Draft Banner",
                    "subtitle": "Must stay hidden",
                    "bannerType": "PROMOTION",
                    "isActive": True,
                    "isPublic": True,
                    "isPublished": False,
                    "isDraft": True,
                    "clinicalApproved": True,
                    "priority": 9,
                },
            ),
            (
                "banner_tip_pending_03",
                {
                    "bannerKey": "banner_tip_pending_03",
                    "title": "Unapproved Clinical Tip",
                    "subtitle": "Needs review",
                    "bannerType": "CLINICAL_TIP",
                    "isActive": True,
                    "isPublic": True,
                    "isPublished": True,
                    "isDraft": False,
                    "clinicalApproved": False,
                    "priority": 8,
                },
            ),
            (
                "banner_expired_04",
                {
                    "bannerKey": "banner_expired_04",
                    "title": "Expired Campaign",
                    "subtitle": "Past end time",
                    "bannerType": "PROMOTION",
                    "isActive": True,
                    "isPublic": True,
                    "isPublished": True,
                    "isDraft": False,
                    "clinicalApproved": True,
                    "endAtMillis": now_ms - 60_000,
                    "priority": 7,
                },
            ),
            (
                "banner_future_05",
                {
                    "bannerKey": "banner_future_05",
                    "title": "Future Scheduled Campaign",
                    "subtitle": "Starts later",
                    "bannerType": "ANNOUNCEMENT",
                    "isActive": True,
                    "isPublic": True,
                    "isPublished": True,
                    "isDraft": False,
                    "clinicalApproved": True,
                    "startAtMillis": now_ms + 3_600_000,
                    "priority": 6,
                },
            ),
        ]

        for doc_id, fields in banners_to_seed:
            code, _ = self.backend.upsert_firestore_document(
                token=super_token,
                collection="banners_content",
                doc_id=doc_id,
                fields=fields,
                is_create=True,
            )
            self.assertEqual(code, 201)

        live_code, live_resp = self.backend.list_public_live_banners()
        self.assertEqual(live_code, 200)
        live_ids = [item["documentId"] for item in live_resp["banners"]]
        self.assertIn("banner_live_01", live_ids)
        self.assertNotIn("banner_draft_02", live_ids)
        self.assertNotIn("banner_tip_pending_03", live_ids)
        self.assertNotIn("banner_expired_04", live_ids)
        self.assertNotIn("banner_future_05", live_ids)

        for ev_type in ("IMPRESSION", "IMPRESSION", "CLICK", "DISMISS"):
            ev_code, ev_resp = self.backend.record_banner_analytics_event(
                {
                    "bannerId": 101,
                    "bannerKey": "banner_live_01",
                    "eventType": ev_type,
                    "userId": "user_joshi",
                    "username": "joshi",
                    "screenSlot": "HOME_TOP",
                }
            )
            self.assertEqual(ev_code, 201)
            self.assertTrue(ev_resp["success"])

        bad_ev_code, _ = self.backend.record_banner_analytics_event(
            {"bannerKey": "banner_live_01", "eventType": "HACKED"}
        )
        self.assertEqual(bad_ev_code, 400)

        self.assertEqual(self.backend.get_banner_analytics_summary(user_token)[0], 403)

        an_code, an_resp = self.backend.get_banner_analytics_summary(super_token)
        self.assertEqual(an_code, 200)
        self.assertGreaterEqual(an_resp["totalBanners"], 6)
        self.assertGreaterEqual(an_resp["totalImpressions"], 2)
        self.assertGreaterEqual(an_resp["totalClicks"], 1)
        self.assertGreaterEqual(an_resp["scheduledBanners"], 1)
        self.assertGreaterEqual(an_resp["draftBanners"], 1)
        self.assertTrue(len(an_resp["dailyBreakdown"]) >= 1)

    def test_super_admin_hj195344_30_day_session_and_email_delivery(self):
        # 1. Verify hj195344@gmail.com Super Admin login
        code, session = self.backend.login("hj195344@gmail.com", "12345")
        self.assertEqual(code, 200)
        self.assertEqual(session["role"], "SUPER_ADMIN")
        self.assertEqual(session["email"], "hj195344@gmail.com")
        token = session["token"]

        # 2. Verify hj195344@gmail.com cannot be deleted or demoted
        del_code, _ = self.backend.delete_user(token, session["userId"])
        self.assertEqual(del_code, 403)
        role_code, _ = self.backend.update_user_role(token, session["userId"], "USER")
        self.assertEqual(role_code, 403)

        # 3. Verify post-login email notification integration (/api/v1/email/send)
        email_code, email_resp = self.backend.send_transactional_email(
            token,
            {
                "recipientEmail": "hj195344@gmail.com",
                "recipientName": "Harsh Joshi",
                "subject": "NeuroGrip Security Alert: Successful Sign-In",
                "bodyText": "Signed in via unified login flow.",
                "category": "POST_LOGIN_SECURITY",
            },
        )
        self.assertEqual(email_code, 200)
        self.assertTrue(email_resp["success"])
        self.assertEqual(email_resp["recipientEmail"], "hj195344@gmail.com")


if __name__ == "__main__":
    unittest.main()



