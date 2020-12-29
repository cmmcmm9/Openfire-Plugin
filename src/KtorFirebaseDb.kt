package com.tapinapp

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.*
import java.io.InputStream
import java.time.LocalDateTime
import kotlin.properties.Delegates

/**
 * Class to handle all of the Firebase Database calls
 * to retrieve Firebase Cloud Messaging (FCM) Tokens stored in the Firebase
 * realtime database. The FCM tokens must be retrieved before a firebase
 * message can be sent to the user.
 */
class KtorFirebaseDb {

    private lateinit var refreshToken: InputStream
    private lateinit var firebaseOptions: FirebaseOptions
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseApp: FirebaseApp
    private val appName = "ktorFirebaseData"


    /**
     * Destroy this firebase app [firebaseApp]
     */
    fun destroyFirebaseApp(){
        firebaseApp.delete()
    }

    /**
     * Generate a one time custom auth token for a user to
     * sign in with, and retrieve their offline messages, or retrieve contact information.
     * Must be given the user's [email] in order to generate a one time token.
     */
    fun generateCustomToken(email: String) :String {
        val firebaseUser = firebaseAuth.getUserByEmail(email)
        return firebaseAuth.createCustomToken(firebaseUser.uid)
    }

    /**
     * Function to retrieve the Firebase Cloud Messaging token for the user identified by [uid]
     * in order to send the message. The token is retrieved from Firebase Database and will be stored in the callback lambda
     * variable [FirebaseMessenger.fcmToken] utilizing the Kotlin Delegated properties.
     */
    fun getFCMToken(uid: String) {
        val database = FirebaseDatabase.getInstance(FirebaseApp.getInstance(appName))
        val ref = database.reference
        val usersRef = ref.child("users").orderByKey().equalTo(uid).limitToFirst(1)

        val valueEventListener = object: ValueEventListener {
            /**
             * This method will be called with a snapshot of the data at this location. It will also be called
             * each time that data changes.
             *
             * @param snapshot The current data at the location
             */
            override fun onDataChange(snapshot: DataSnapshot?) {
                snapshot?.children?.forEach{
                    FirebaseMessenger.fcmToken = it.child("token").value as String
                }
                if(FirebaseMessenger.fcmToken.isEmpty()) KtorLogger.logText("Unable to retrieve FCM token for user $uid")

                else KtorLogger.logText("Retrieved token from Firebase for user $uid and token was ${FirebaseMessenger.fcmToken}")
            }

            /**
             * This method will be triggered in the event that this listener either failed at the server, or
             * is removed as a result of the security and Firebase Database rules. For more information on
             * securing your data, see:
             * [
 * Security Quickstart](https://firebase.google.com/docs/database/security/quickstart)
             *
             * @param error A description of the error that occurred
             */
            override fun onCancelled(error: DatabaseError?) {
                KtorLogger.logText("Firebase Query Failed. Full stack Trace:")
                KtorLogger.logTextNoDate(error?.message.toString())
                KtorLogger.logTextNoDate(error?.details.toString())
                KtorLogger.logTextNoDate(error?.code.toString())
            }

        }
        usersRef.addListenerForSingleValueEvent(valueEventListener)
    }

    /**
     * Kotlin object to handle sending Firebase Messages to users.
     */
    object FirebaseMessenger {
        //varibles to store the message type and extra properties
        private var messageType = ""
        private var extraProperties = mutableMapOf<String, String>()

        //callback lambda function to react to a new token value retrieved from [KtorFirebaseDb.getFCMToken()]
        var fcmToken: String by Delegates.observable("") { property, oldValue, newValue ->
            println("FIRE TOKEN CHANGED")
            sendMessage()
        }

        private val kotlinFirebaseDb = getKtorFirebaseDbInstance()


        /**
         * Function to setup a new Firebase message for an offline single message.
         * Will trigger the retrieval for the firebase token stored for the user at node
         * [openFireUsername] and a custom authToken will be generated for the email [email].
         */
        fun sendOfflineRetrievalMessage(email: String, openFireUsername: String, messageType: String){
            synchronized(this){
                this.messageType = messageType
                extraProperties["customToken"] = kotlinFirebaseDb.generateCustomToken(email = email)
                kotlinFirebaseDb.getFCMToken(uid = openFireUsername)
            }


        }

        /**
         * Function to setup a new Firebase message for a vcard update.
         * Will trigger the retrieval for the firebase token stored for the user at node
         * [openFireUsername] and a custom authToken will be generated for the email [email].
         */
        fun sendVcardUpdateMessage(email: String, openFireUsername: String, messageType: String, contactJID: String){
            synchronized(this){
                this.messageType = messageType
                extraProperties["contactJID"] = contactJID
                extraProperties["customToken"] = kotlinFirebaseDb.generateCustomToken(email = email)
                kotlinFirebaseDb.getFCMToken(uid = openFireUsername)
            }

        }

        /**
         * Function to setup a new Firebase message for an offline MUC (multi-user chat) message.
         * Will trigger the retrieval for the firebase token stored for the user at node
         * [openFireUsername] and a custom authToken will be generated for the email [email].
         */
        fun sendOfflienMUCMessage(email: String, openFireUsername: String, messageType: String, roomJID: String){
            synchronized(this){
                this.messageType = messageType
                extraProperties["roomJID"] = roomJID
                extraProperties["customToken"] = kotlinFirebaseDb.generateCustomToken(email = email)
                kotlinFirebaseDb.getFCMToken(uid = openFireUsername)
            }

        }

        /**
         * Function to setup a new Firebase message for a new avatar change, either for a
         * group chat avatar or a user's avatar.
         * Will trigger the retrieval for the firebase token stored for the user at node
         * [openFireUsername] and send the [avatarURL] that needs be updated on the client side.
         */
        fun sendUpdateAvatarMessage(openFireUsername: String, avatarURL: String){
            synchronized(this){
                this.messageType = "update-avatar"
                extraProperties["avatar-url"] = avatarURL
                kotlinFirebaseDb.getFCMToken(uid = openFireUsername)
            }
        }

        /**
         * Function to actually send the FCM configured by the above functions.
         * Triggered by the lambda expresion [fcmToken]. This is done because Firebase only allows
         * non-blocking calls, so must register callback for token retrieval.
         */
        private fun sendMessage(){
            synchronized(this){
                if(fcmToken.isEmpty()) return
                val fcmMessenger = FirebaseMessaging.getInstance(FirebaseApp.getInstance(kotlinFirebaseDb.appName))


                val fcmMessageBuilder = Message.builder()
                    .putData("KtorMessage", messageType)
                    .putData("Time", LocalDateTime.now().format(KtorLogger.formatter).toString())
                    .setToken(fcmToken)

                if(!extraProperties.isNullOrEmpty()) fcmMessageBuilder.putAllData(extraProperties)
                extraProperties = mutableMapOf()
                val fcmMessage = fcmMessageBuilder.build()

                val result = fcmMessenger.send(fcmMessage)
                KtorLogger.logText("The result of the message is: $result")

            }
            }


    }


    /**
     * Kotlin companion object to ensure the single instance of this class.
     * Instance gotten through [getKtorFirebaseDbInstance]
     */
    companion object{


        @Volatile
        private var INSTANCE: KtorFirebaseDb? = null


        /**
         * Function to retrieve the singleton instance of [KtorFirebaseDb].
         * If an instance does not exist, it will create one. Otherwise
         * it will return the existing instance
         */
        fun getKtorFirebaseDbInstance(): KtorFirebaseDb {
            val tempInstance = INSTANCE
            if(tempInstance != null) return tempInstance

            synchronized(this){
                val instance = KtorFirebaseDb()

                val checkFirebaseApp = runCatching { FirebaseApp.getInstance(instance.appName) }

                checkFirebaseApp.onFailure {
                    instance.refreshToken = CredentialManager.getResourceToken() //File("/home/colby/Desktop/JarForOpenfire/resources/tapin-c0ba6-firebase-adminsdk-wcwb2-f27fea915a.json").inputStream()
                    instance.firebaseOptions = FirebaseOptions.Builder()
                        .setCredentials(GoogleCredentials.fromStream(instance.refreshToken))
                        .setDatabaseUrl("https://tapin-c0ba6.firebaseio.com")
                        .build()
                    instance.firebaseApp = FirebaseApp.initializeApp(instance.firebaseOptions, instance.appName)
                    instance.firebaseAuth = FirebaseAuth.getInstance(instance.firebaseApp)
                    CredentialManager.closeStream()
                    INSTANCE = instance
                }

                checkFirebaseApp.onSuccess {
                    instance.firebaseApp = it
                    instance.firebaseAuth = FirebaseAuth.getInstance(it)
                    INSTANCE = instance
                }

                return instance
            }

        }
    }
}


