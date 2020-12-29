package com.tapinapp



import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jivesoftware.openfire.OfflineMessageStrategy
import org.jivesoftware.openfire.container.Plugin
import org.jivesoftware.openfire.container.PluginManager
import org.jivesoftware.openfire.muc.MUCEventDispatcher
import org.jivesoftware.openfire.vcard.VCardEventDispatcher
import java.io.File
import java.security.KeyStore
import java.util.concurrent.TimeUnit

/**
 * Class that launches the embedded Ktor server
 * within Openfire. Will launch the Ktor server on a separate
 * co-routine thread, to ensure Openfire is not blocked on its
 * own thread. Is using the [Plugin] interface in order to
 * be properly launched by Openfire.
 */
@Suppress("Unused")
class TapinPlugin :Plugin  {

    private val tapInMucEventListener = TapInMucEventListener()
    private val offlineMessageHandler = OfflineMessageHandler()
    private val vCardUpdater = VCardUpdateEventListener()
    private val file = File("/etc/letsencrypt/live/tapinapp.com/keystore.jks")

    private val password = CredentialManager.getDbPassword()?.toCharArray()
    private val keyStore = KeyStore.getInstance(file, password)

    /**
     * Server environment setup for Ktor embedded server.
     * Using Kotlin Delegated property [lazy] to ensure a thread
     * safe reference is used for the server environment variable. Solves synchronization issues
     * on multiple threads.
     */
    private val env by lazy {
        applicationEngineEnvironment {
            module {
                this.main()
            }
            //only accessible from local host
            connector {
                host = "127.0.0.1"
                port = 8080
            }
            //SSL port, accessible from anywhere
            sslConnector(keyStore =  keyStore, keyAlias = "ktor", keyStorePassword = { password!! }, privateKeyPassword = { password!! } ) {
                port = 8090
                host = "0.0.0.0"
                keyStorePath = file
            }
        }
    }

    /**
     * Embedded Ktor Server.
     * Using Kotlin Delegated property [lazy] to ensure a thread
     * safe reference is used for the server variable. Solves synchronization issues
     * on multiple threads.
     */
    private val server by lazy {
        embeddedServer(Netty, env)
    }


    /**
     * Called when the Ktor Plugin is about to be destroyed.
     * Will stop the Ktor server (with 1 second grade period)
     * as well as removing all of the event listeners, such as
     * [OfflineMessageHandler], [VCardUpdateEventListener], [TapInMucEventListener].
     * Will also destroy the firebase apps, made by [KtorFirebaseDb] and
     * [KtorFirebaseAuth].
     */
    override fun destroyPlugin() {
        KtorLogger.logText("ABOUT TO DESTROY THE PLUGIN")
        server.stop(1L, 1L, TimeUnit.SECONDS)
        MUCEventDispatcher.removeListener(tapInMucEventListener)
        OfflineMessageStrategy.removeListener(offlineMessageHandler)
        VCardEventDispatcher.removeListener(vCardUpdater)
        KtorFirebaseAuth.getKotlinFirebaseInstance().destroyFirebaseApp()
        KtorFirebaseDb.getKtorFirebaseDbInstance().destroyFirebaseApp()

    }

    /**
     * Function called when the plugin is initialized by Openfire.
     * Will start the embedded [server] on a separate co-routine. If it
     * fails to launch the Ktor server, it will log the error message using [KtorLogger].
     * Will also attempt to create the single instances of [KtorFirebaseAuth]
     * and [KtorFirebaseDb], or log any errors that occur.
     * Lastly, assuming all of the previous initializations have succeeded,
     * then the listeners [VCardUpdateEventListener], [OfflineMessageHandler],
     * and [TapInMucEventListener] will be registered to Openfire so they can respond
     * to all of the required events.
     *
     */
    override fun initializePlugin(manager: PluginManager?, pluginFile: File?) {

        KtorLogger.logText("IN THE PLUGIN RIGHT BEFORE LAUNCHING THE SERVER")
        val startFirebaseAuth = kotlin.runCatching { KtorFirebaseAuth.getKotlinFirebaseInstance() }
        val startFirebaseDb = kotlin.runCatching { KtorFirebaseDb.getKtorFirebaseDbInstance() }

        startFirebaseAuth.onFailure { throwable ->
            KtorLogger.logText("Firebase Ktor Auth Failed")
            KtorLogger.logText("${throwable.message}")
            KtorLogger.logText("${throwable.cause}")
            throwable.stackTrace.forEach {
                KtorLogger.logText("$it")
            }
        }
        startFirebaseAuth.onSuccess {
            KtorLogger.logText("Kotlin Firebase Ktor Auth Was able to be created. ")
        }

        startFirebaseDb.onFailure { throwable ->
            KtorLogger.logText("Firebase Ktor DB Failed")
            KtorLogger.logText("${throwable.message}")
            KtorLogger.logText("${throwable.cause}")
            throwable.stackTrace.forEach {
                KtorLogger.logText("$it")
            }
        }
        startFirebaseDb.onSuccess {
            KtorLogger.logText("Kotlin Firebase Ktor DB Was able to be created. ")
        }

        GlobalScope.launch {

            val startServer = kotlin.runCatching {server.start(wait = false)}
            startServer.onSuccess {
                KtorLogger.logText("Ktor Server Was Successfully Launched")
            }
            startServer.onFailure { throwable ->
                KtorLogger.logText("Failed to start server. Message:  ${throwable.message} caused by ${throwable.cause}")
                KtorLogger.logText("Localized Message: ${throwable.localizedMessage}")
                KtorLogger.logText("StackTrace: ")
                throwable.stackTrace.forEach {
                    KtorLogger.logText("$it")
                }
            }
            KtorLogger.logText("Co-routine to launch server has been initiated")
        }
        KtorLogger.logText("About to register Listeners")
        MUCEventDispatcher.addListener(tapInMucEventListener)
        OfflineMessageStrategy.addListener(offlineMessageHandler)
        VCardEventDispatcher.addListener(vCardUpdater)


    }


}