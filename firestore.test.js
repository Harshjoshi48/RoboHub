const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_hj123";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const validTimestamp = () => new Date(Date.now() - 1000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedAdmin(uid = ADMIN_UID) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(uid).set({
      uid,
      email: "hj195345@gmail.com",
      role: "SUPER_ADMIN",
      createdAt: validTimestamp(),
    });
  });
}

test("1. Unauthenticated user: cannot read users or remote_config", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("remote_config").doc("current").get());
});

test("2. Cross-user PII isolation: Alice cannot read Bob's user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob",
      name: "Bob Patient",
      email: "bob@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("3. Query Trust: Non-admin user fails unfiltered list on users, succeeds with userId filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice Patient",
      email: "alice@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertSucceeds(
    aliceDb.collection("users").where("userId", "==", ALICE_UID).get()
  );
});

test("4. Privilege escalation blocked: User cannot self-assign SUPER_ADMIN role on create or update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice",
      email: "alice@neurogrip.io",
      role: "SUPER_ADMIN",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice",
      email: "alice@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    })
  );

  // Attempting to escalate role via update must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: validTimestamp(),
    })
  );
});

test("5. Shadow Update blocked: Undeclared ghost field is rejected", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(
    adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob",
      name: "Bob",
      email: "bob@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      ghostAdminFlag: true,
      createdAt: validTimestamp(),
    })
  );
});

test("6. Email Spoofing blocked: Unverified bootstrapped admin email is rejected, while verified hj195345@gmail.com succeeds", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "hj195345@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofDb.collection("remote_config").doc("current").set({
      configKey: "current",
      maintenanceMode: true,
      maintenanceMessage: "Hacked",
      minSupportedVersionCode: 1,
      minSupportedVersionName: "1.0.0",
      recommendedVersionName: "1.2.0",
      configVersion: 2,
      updatedBy: "spoof",
      createdAt: validTimestamp(),
    })
  );

  const verifiedSuperAdminDb = testEnv
    .authenticatedContext("verified_hj195345_uid", {
      email: "hj195345@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    verifiedSuperAdminDb.collection("remote_config").doc("current").set({
      configKey: "current",
      maintenanceMode: false,
      maintenanceMessage: "Verified Super Admin",
      minSupportedVersionCode: 1,
      minSupportedVersionName: "1.0.0",
      recommendedVersionName: "1.2.0",
      configVersion: 2,
      updatedBy: "hj195345@gmail.com",
      createdAt: validTimestamp(),
    })
  );
});

test("7. Verified Admin can CRUD allowlisted collections and nested maps/arrays", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertSucceeds(
    adminDb.collection("remote_config").doc("current").set({
      configKey: "current",
      maintenanceMode: false,
      maintenanceMessage: "Nominal",
      minSupportedVersionCode: 1,
      minSupportedVersionName: "1.0.0",
      recommendedVersionName: "1.2.0",
      configVersion: 1,
      updatedBy: "hj123",
      metadata: { region: "asia-southeast1", cluster: "neuro-1" },
      tags: ["production", "v1"],
      createdAt: validTimestamp(),
    })
  );

  await assertSucceeds(adminDb.collection("users").get());
});

test("8. Audit logs are append-only: update and delete are rejected even for Admin", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  await assertSucceeds(
    adminDb.collection("audit_logs").doc("log_1").set({
      actorUid: ADMIN_UID,
      actorUsername: "hj123",
      actorRole: "SUPER_ADMIN",
      actionType: "DB_CREATE",
      targetEntity: "remote_config/current",
      details: "Initialized remote config",
      severity: "INFO",
      createdAt: validTimestamp(),
    })
  );

  await assertFails(
    adminDb.collection("audit_logs").doc("log_1").update({
      details: "Tampered audit log",
    })
  );
  await assertFails(adminDb.collection("audit_logs").doc("log_1").delete());
});

test("9. Protected system collection outside allowlist is denied for everyone", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(
    adminDb.collection("system_credentials").doc("secret_1").get()
  );
  await assertFails(
    adminDb.collection("system_credentials").doc("secret_1").set({ key: "val" })
  );
});

test("10. System Config (system_config/runtime_settings): Super Admin can write valid runtime settings, secrets and invalid ranges are rejected, non-admin denied", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const validSysConfig = {
    configKey: "runtime_settings",
    otpTtlSeconds: 60,
    otpMaxSends: 3,
    otpWindowSeconds: 600,
    sessionTtlMinutes: 30,
    rateLimitPerMinute: 60,
    requireTwoFactorForAdmins: false,
    idleLockoutMinutes: 15,
    fcmMasterEnabled: true,
    fcmDefaultTopic: "ALL_USERS",
    highPriorityClinicalAlerts: true,
    preserveDefaultScreenLayout: true,
    maintenanceMode: false,
    maintenanceMessage: "Nominal",
    minSupportedVersionName: "1.0.0",
    recommendedVersionName: "1.2.0",
    forceUpdate: false,
    configVersion: 1,
    updatedBy: "hj123",
    createdAt: validTimestamp(),
  };

  // Non-admin cannot read or write system_config
  await assertFails(
    aliceDb.collection("system_config").doc("runtime_settings").set(validSysConfig)
  );

  // Admin can write valid system_config
  await assertSucceeds(
    adminDb.collection("system_config").doc("runtime_settings").set(validSysConfig)
  );
  await assertSucceeds(
    adminDb.collection("system_config").doc("runtime_settings").get()
  );

  // Out-of-range OTP_TTL_SECONDS (< 15) is rejected by Firestore rules
  await assertFails(
    adminDb.collection("system_config").doc("runtime_settings").set({
      ...validSysConfig,
      otpTtlSeconds: 5,
    })
  );

  // Attempting to store a secret key (adminHmacSecret) in system_config is rejected by hasOnly()
  await assertFails(
    adminDb.collection("system_config").doc("runtime_settings").set({
      ...validSysConfig,
      adminHmacSecret: "leaked_secret",
    })
  );

  // Deleting system_config is forbidden even for Admin
  await assertFails(
    adminDb.collection("system_config").doc("runtime_settings").delete()
  );
});

test("11. Self-Delete & Audit Log Role Spoofing Protection: User can delete own USER profile, cannot delete Bob's profile, and cannot spoof SUPER_ADMIN in audit_logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice Patient",
      email: "alice@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob",
      name: "Bob Patient",
      email: "bob@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      createdAt: validTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Alice cannot delete Bob's account
  await assertFails(aliceDb.collection("users").doc(BOB_UID).delete());

  // Alice cannot forge an audit log claiming actorRole == SUPER_ADMIN
  await assertFails(
    aliceDb.collection("audit_logs").doc("spoof_log_1").set({
      actorUid: ALICE_UID,
      actorUsername: "alice",
      actorRole: "SUPER_ADMIN",
      actionType: "ACCOUNT_SELF_DELETE",
      targetEntity: `user:${ALICE_UID}`,
      details: "Attempted role spoofing in audit log",
      severity: "WARNING",
      createdAt: validTimestamp(),
    })
  );

  // Alice can write her own audit log with actorRole == USER
  await assertSucceeds(
    aliceDb.collection("audit_logs").doc("alice_log_1").set({
      actorUid: ALICE_UID,
      actorUsername: "alice",
      actorRole: "USER",
      actionType: "ACCOUNT_SELF_DELETE",
      targetEntity: `user:${ALICE_UID}`,
      details: "User permanently deleted own account",
      severity: "INFO",
      createdAt: validTimestamp(),
    })
  );

  // Alice can permanently delete her own USER profile document
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("12. Server-Driven UI (sdui_configs & sdui_history): Admin can publish valid SDUI, invalid colors/URLs/schemas rejected, signed-in user can read but cannot write", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const validSduiDoc = {
    configKey: "dashboard_active",
    schemaVersion: 1,
    configVersion: 2,
    updatedBy: "hj123",
    changeSummary: "Updated hero banner and promo strip",
    payloadJson: "{\"configKey\":\"dashboard_active\",\"schemaVersion\":1,\"configVersion\":2}",
    contentSha256: "a".repeat(64),
    themePrimaryHex: "#2563EB",
    themeBackgroundHex: "#F5F7FA",
    heroTitle: "NeuroGrip Connected Hub",
    heroImageUrl: "https://images.unsplash.com/photo-1589254065878-42c9da997008",
    promoEnabled: true,
    sectionsOrder: ["promo_banner", "hero", "ai_assistant"],
    createdAt: validTimestamp(),
  };

  // Unauthenticated read/write is denied
  await assertFails(
    unauthDb.collection("sdui_configs").doc("dashboard_active").get()
  );

  // Non-admin user cannot write or tamper with SDUI
  await assertFails(
    aliceDb.collection("sdui_configs").doc("dashboard_active").set(validSduiDoc)
  );

  // Admin can publish valid SDUI config and history snapshot
  await assertSucceeds(
    adminDb.collection("sdui_configs").doc("dashboard_active").set(validSduiDoc)
  );
  await assertSucceeds(
    adminDb.collection("sdui_history").doc("sdui_v2").set({
      configVersion: 2,
      schemaVersion: 1,
      summary: "Updated hero banner and promo strip",
      publishedBy: "hj123",
      payloadJson: validSduiDoc.payloadJson,
      contentSha256: validSduiDoc.contentSha256,
      createdAt: validTimestamp(),
    })
  );

  // Signed-in user can read active SDUI config but cannot read admin-only sdui_history
  await assertSucceeds(
    aliceDb.collection("sdui_configs").doc("dashboard_active").get()
  );
  await assertFails(
    aliceDb.collection("sdui_history").doc("sdui_v2").get()
  );

  // Invalid hex color, non-https image URL, or unsupported schemaVersion rejected even for admin
  await assertFails(
    adminDb.collection("sdui_configs").doc("dashboard_active").set({
      ...validSduiDoc,
      themePrimaryHex: "not-a-hex",
    })
  );
  await assertFails(
    adminDb.collection("sdui_configs").doc("dashboard_active").set({
      ...validSduiDoc,
      heroImageUrl: "http://insecure.example.com/a.png",
    })
  );
  await assertFails(
    adminDb.collection("sdui_configs").doc("dashboard_active").set({
      ...validSduiDoc,
      schemaVersion: 99,
    })
  );

  // Deleting active SDUI config or updating immutable history is forbidden
  await assertFails(
    adminDb.collection("sdui_configs").doc("dashboard_active").delete()
  );
  await assertFails(
    adminDb.collection("sdui_history").doc("sdui_v2").update({ summary: "tampered" })
  );
});

test("13. Dynamic Banner Management (banners_content & banner_analytics_events): Live vs Draft/Unapproved isolation, invalid HEX/CTA rejected, immutable user analytics events enforced", async () => {
  await seedAdmin();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const liveBanner = {
    bannerKey: "banner_promo_1",
    title: "NeuroGrip v2.5 Calibration",
    subtitle: "Improved grip force response now available.",
    description: "Run guided 5-finger calibration in Hand Control.",
    bannerType: "PROMOTION",
    iconKey: "campaign",
    imageUrl: "https://images.unsplash.com/photo-1589254065878-42c9da997008",
    ctaEnabled: true,
    ctaText: "Open Control",
    ctaActionType: "OPEN_SCREEN",
    ctaTargetValue: "hand_control",
    deepLinkRoute: "hand_control",
    targetScreen: "HOME_TOP",
    displayPosition: "HOME_TOP",
    backgroundHex: "#1E40AF",
    textHex: "#FFFFFF",
    buttonHex: "#FFFFFF",
    buttonTextHex: "#1E40AF",
    borderHex: "#3B82F6",
    priority: 10,
    isActive: true,
    isPublic: true,
    isPublished: true,
    isDraft: false,
    clinicalReviewStatus: "NOT_REQUIRED",
    clinicalApproved: true,
    targetAudience: "PUBLIC_ALL",
    dismissible: true,
    reShowAfterDismissHours: 24,
    impressionsCount: 0,
    uniqueViewsCount: 0,
    clicksCount: 0,
    createdBy: "hj123",
    updatedBy: "hj123",
    createdAt: validTimestamp(),
  };

  const draftClinicTip = {
    ...liveBanner,
    bannerKey: "banner_tip_draft",
    title: "Unapproved Clinical Tip",
    bannerType: "CLINICAL_TIP",
    isDraft: true,
    isPublished: false,
    clinicalReviewStatus: "PENDING_REVIEW",
    clinicalApproved: false,
  };

  // Non-admin cannot create or modify banners
  await assertFails(aliceDb.collection("banners_content").doc("banner_promo_1").set(liveBanner));

  // Admin can create live & draft banners
  await assertSucceeds(adminDb.collection("banners_content").doc("banner_promo_1").set(liveBanner));
  await assertSucceeds(adminDb.collection("banners_content").doc("banner_tip_draft").set(draftClinicTip));

  // Invalid HEX color or disallowed CTA action rejected even for Admin
  await assertFails(
    adminDb.collection("banners_content").doc("banner_bad_hex").set({
      ...liveBanner,
      backgroundHex: "red-invalid",
    })
  );
  await assertFails(
    adminDb.collection("banners_content").doc("banner_bad_cta").set({
      ...liveBanner,
      ctaActionType: "EXEC_SHELL",
    })
  );

  // Regular user can read public+published+active+approved banner, CANNOT read draft/unapproved banner or unfiltered collection
  await assertSucceeds(aliceDb.collection("banners_content").doc("banner_promo_1").get());
  await assertFails(aliceDb.collection("banners_content").doc("banner_tip_draft").get());
  await assertFails(aliceDb.collection("banners_content").get());
  await assertSucceeds(
    aliceDb.collection("banners_content")
      .where("isActive", "==", true)
      .where("isPublic", "==", true)
      .where("isPublished", "==", true)
      .where("isDraft", "==", false)
      .where("clinicalApproved", "==", true)
      .get()
  );

  // Admin can list all banners including drafts
  await assertSucceeds(adminDb.collection("banners_content").get());

  // Signed-in user can append own analytics event, cannot spoof another user or mutate/delete events
  const aliceEvent = {
    bannerId: 1,
    bannerKey: "banner_promo_1",
    eventType: "IMPRESSION",
    userId: ALICE_UID,
    username: "alice",
    dateKey: "2026-11-15",
    screenSlot: "HOME_TOP",
    createdAt: validTimestamp(),
  };
  await assertSucceeds(aliceDb.collection("banner_analytics_events").doc("evt_alice_1").set(aliceEvent));
  await assertFails(
    aliceDb.collection("banner_analytics_events").doc("evt_spoof").set({
      ...aliceEvent,
      userId: BOB_UID,
    })
  );
  await assertFails(bobDb.collection("banner_analytics_events").doc("evt_alice_1").get());
  await assertFails(adminDb.collection("banner_analytics_events").doc("evt_alice_1").update({ eventType: "CLICK" }));
  await assertFails(adminDb.collection("banner_analytics_events").doc("evt_alice_1").delete());
  await assertSucceeds(adminDb.collection("banner_analytics_events").get());
});

test("11. Profile Avatar photoUrl & photoUpdatedAt: owner HTTPS update/removal allowed, cross-user & invalid URLs rejected", async () => {
  await seedAdmin();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice Clinician",
      email: "alice@neurogrip.io",
      role: "USER",
      status: "ACTIVE",
      photoUrl: "",
      photoUpdatedAt: 0,
      createdAt: validTimestamp(),
    })
  );

  // Owner can set valid Firebase Storage HTTPS download URL and timestamp
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/profile_photos%2Falice_123%2Favatar_1750000000.jpg?alt=media",
      photoUpdatedAt: 1750000000,
    })
  );

  // Owner can remove photo by clearing photoUrl to empty string
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "",
      photoUpdatedAt: 1750000100,
    })
  );

  // Local file:// or non-HTTPS URLs must be rejected on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "file:///data/user/0/com.example/files/avatar.jpg",
      photoUpdatedAt: 1750000200,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "http://insecure.example.com/avatar.jpg",
      photoUpdatedAt: 1750000200,
    })
  );

  // Negative timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "",
      photoUpdatedAt: -1,
    })
  );

  // Role escalation combined with photo update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "",
      photoUpdatedAt: 1750000300,
      role: "SUPER_ADMIN",
    })
  );

  // Cross-user photo modification rejected
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/spoof.jpg",
      photoUpdatedAt: 1750000400,
    })
  );
});

test("14. App Branding (app_settings/branding): Super Admin only writes, non-Super-Admin ADMIN/USER denied, signed-in read allowed, invalid payloads & delete rejected", async () => {
  await seedAdmin();
  const REGULAR_ADMIN_UID = "regular_admin_789";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(REGULAR_ADMIN_UID).set({
      uid: REGULAR_ADMIN_UID,
      email: "ops.admin@neurogrip.io",
      role: "ADMIN",
      createdAt: validTimestamp(),
    });
  });

  const superAdminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const regularAdminDb = testEnv.authenticatedContext(REGULAR_ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const validBranding = {
    configKey: "branding",
    appName: "NeuroGrip Pro",
    logoUrl: "https://firebasestorage.googleapis.com/v0/b/neurogrip/o/app_branding%2Flogo_v2_1750000000.png?alt=media",
    logoVersion: 2,
    brandingVersion: 3,
    updatedBy: "hj195345@gmail.com",
    createdAt: validTimestamp(),
  };

  // Unauthenticated read/write denied
  await assertFails(unauthDb.collection("app_settings").doc("branding").get());
  await assertFails(unauthDb.collection("app_settings").doc("branding").set(validBranding));

  // Regular user & non-Super-Admin ADMIN cannot write
  await assertFails(aliceDb.collection("app_settings").doc("branding").set(validBranding));
  await assertFails(regularAdminDb.collection("app_settings").doc("branding").set(validBranding));

  // Verified SUPER_ADMIN can create & update app_settings/branding
  await assertSucceeds(superAdminDb.collection("app_settings").doc("branding").set(validBranding));
  await assertSucceeds(
    superAdminDb.collection("app_settings").doc("branding").update({
      appName: "NeuroGrip Care",
      brandingVersion: 4,
      updatedAt: validTimestamp(),
    })
  );

  // Signed-in user can read branding document & query configKey == 'branding'
  await assertSucceeds(aliceDb.collection("app_settings").doc("branding").get());
  await assertSucceeds(
    aliceDb.collection("app_settings").where("configKey", "==", "branding").get()
  );

  // Invalid appName (> 30 chars or empty), non-https URL, extra field, or wrong doc ID rejected even for Super Admin
  await assertFails(
    superAdminDb.collection("app_settings").doc("branding").set({
      ...validBranding,
      appName: "A".repeat(31),
    })
  );
  await assertFails(
    superAdminDb.collection("app_settings").doc("branding").set({
      ...validBranding,
      logoUrl: "http://insecure.example.com/logo.png",
    })
  );
  await assertFails(
    superAdminDb.collection("app_settings").doc("branding").set({
      ...validBranding,
      unexpectedSecret: "nope",
    })
  );
  await assertFails(
    superAdminDb.collection("app_settings").doc("other_doc").set(validBranding)
  );

  // Delete is always forbidden
  await assertFails(superAdminDb.collection("app_settings").doc("branding").delete());
});
