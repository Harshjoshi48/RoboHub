package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class FirestoreEmulatorTestBase {

    companion object {
        const val PROJECT_ID = "demo-no-project"
        const val FIRESTORE_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_HOST = "127.0.0.1"
        const val AUTH_PORT = 9099

        private var sharedApp: FirebaseApp? = null
        private var sharedAuth: FirebaseAuth? = null
        private var sharedDb: FirebaseFirestore? = null
    }

    protected lateinit var app: FirebaseApp
    protected lateinit var db: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    fun baseSetUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseId = context.getString(R.string.firestore_database_id)

        if (sharedApp == null) {
            val options = FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:1234567890:android:abcdef")
                .setApiKey("fake-api-key-for-emulator")
                .build()

            val initializedApp = FirebaseApp.initializeApp(context, options, "test-${UUID.randomUUID()}")
            val initializedAuth = FirebaseAuth.getInstance(initializedApp).apply {
                useEmulator(AUTH_HOST, AUTH_PORT)
            }
            val initializedDb = FirebaseFirestore.getInstance(initializedApp, databaseId).apply {
                useEmulator(FIRESTORE_HOST, FIRESTORE_PORT)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            sharedApp = initializedApp
            sharedAuth = initializedAuth
            sharedDb = initializedDb
        }

        app = requireNotNull(sharedApp)
        auth = requireNotNull(sharedAuth)
        db = requireNotNull(sharedDb)

        auth.signOut()
        clearFirestoreData()
    }

    @After
    fun baseTearDown() {
        if (::auth.isInitialized) {
            auth.signOut()
        }
    }

    /**
     * Executes a suspend test block on Dispatchers.IO while pumping Robolectric's main Looper
     * so Firebase Task continuations posted to TaskExecutors.MAIN_THREAD complete immediately.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    protected fun runFirestoreTest(
        timeoutMs: Long = 30_000L,
        block: suspend CoroutineScope.() -> Unit
    ) {
        val deferred = CoroutineScope(Dispatchers.IO).async {
            block()
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        val shadowMainLooper = Shadows.shadowOf(Looper.getMainLooper())
        while (deferred.isActive && System.currentTimeMillis() < deadline) {
            shadowMainLooper.idle()
            Thread.sleep(10)
        }
        shadowMainLooper.idle()
        if (deferred.isActive) {
            deferred.cancel()
            throw AssertionError("Firestore test timed out after ${timeoutMs}ms")
        }
        val completionException = deferred.getCompletionExceptionOrNull()
        if (completionException != null) {
            throw completionException
        }
    }

    protected suspend fun signInAs(email: String, password: String = "TestPass123!"): String {
        val result = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
        return requireNotNull(result.user?.uid)
    }

    private fun clearFirestoreData() {
        val url = URL(
            "http://$FIRESTORE_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/$databaseId/documents"
        )
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "DELETE"
            connectTimeout = 5000
            readTimeout = 5000
            check(responseCode in 200..299) {
                "Failed to clear Firestore emulator (DB=$databaseId): HTTP $responseCode"
            }
            disconnect()
        }
    }
}
