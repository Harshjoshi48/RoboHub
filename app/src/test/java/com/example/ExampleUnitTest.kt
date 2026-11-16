package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun passwordHasher_verifiesAdminAndUserCredentials() {
        val adminHash = SecurityHasher.hashPassword("12345")
        assertTrue(SecurityHasher.verifyPassword("12345", adminHash))
        assertFalse(SecurityHasher.verifyPassword("wrong", adminHash))
    }

    @Test
    fun rbacPolicy_enforcesRoleHierarchy() {
        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.SUPER_ADMIN, AdminPermission.MANAGE_ROLES))
        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.SUPER_ADMIN, AdminPermission.MANAGE_BACKUPS))

        assertFalse(RolePermissionPolicy.hasPermission(AdminRole.ADMIN, AdminPermission.MANAGE_ROLES))
        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.ADMIN, AdminPermission.SUSPEND_USERS))

        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.EDITOR, AdminPermission.MANAGE_BANNERS))
        assertFalse(RolePermissionPolicy.hasPermission(AdminRole.EDITOR, AdminPermission.DELETE_USERS))

        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.SUPPORT_AGENT, AdminPermission.MANAGE_SUPPORT_TICKETS))
        assertFalse(RolePermissionPolicy.hasPermission(AdminRole.SUPPORT_AGENT, AdminPermission.MANAGE_REMOTE_CONFIG))

        assertTrue(RolePermissionPolicy.hasPermission(AdminRole.ANALYST, AdminPermission.VIEW_ANALYTICS))
        assertFalse(RolePermissionPolicy.hasPermission(AdminRole.ANALYST, AdminPermission.SUSPEND_USERS))

        assertTrue(RolePermissionPolicy.permissionsFor(AdminRole.USER).isEmpty())
    }

    @Test
    fun securityErrorSanitizer_stripsStackTracesFilePathsTokensAndFormatsErrorCodes() {
        val permEx = RuntimeException(
            "PERMISSION_DENIED: Missing or insufficient permissions at /data/user/0/com.example/databases/neurogrip_unified.db Bearer secret_token_123"
        )
        val safePermMsg = SecurityErrorSanitizer.toUserSafeMessage(permEx)
        assertTrue(safePermMsg.contains("[Code: DB_403]"))
        assertFalse(safePermMsg.contains("/data/user/0"))
        assertFalse(safePermMsg.contains("secret_token_123"))

        val unauthEx = IllegalStateException("UNAUTHENTICATED: Session expired for Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")
        val safeAuthMsg = SecurityErrorSanitizer.toUserSafeMessage(unauthEx)
        assertTrue(safeAuthMsg.contains("[Code: AUTH_401]"))
        assertFalse(safeAuthMsg.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))

        val internalEx = RuntimeException(
            "java.sql.SQLException: constraint failed at com.example.AdminRepository.login(AdminRepository.kt:412) in /app/applet/secret.key"
        )
        val safeSysMsg = SecurityErrorSanitizer.toUserSafeMessage(internalEx, "Database operation failed")
        assertEquals("Database operation failed. Please try again. [Code: SYS_500]", safeSysMsg)

        val logSanitized = SecurityErrorSanitizer.sanitizeLogDetails(
            "Error at /data/user/0/com.example/files/report.pdf with Bearer my_secret_jwt_999 at com.example.Foo(Foo.kt:10)"
        )
        assertFalse(logSanitized.contains("/data/user/0"))
        assertFalse(logSanitized.contains("my_secret_jwt_999"))
        assertTrue(logSanitized.contains("[REDACTED_PATH]"))
        assertTrue(logSanitized.contains("Bearer [REDACTED]"))

        assertEquals("hj***5@gmail.com", SecurityErrorSanitizer.maskEmail("hj195345@gmail.com"))
        assertEquals("abc***789", SecurityErrorSanitizer.maskUid("abcdef123456789"))
    }
}
