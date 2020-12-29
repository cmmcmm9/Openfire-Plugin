package com.tapinapp

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.*
import org.jivesoftware.openfire.XMPPServer
import org.xmpp.packet.JID
import java.io.InputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDateTime

/**
 * This class handles various Firebase Authentication and user
 * management related tasks. It is launching an instance of a Firebase
 * app using the Firebase Admin SDK to help manage users.
 * It can verify a firebase token via @see[checkToken],
 * destroy the firebase app, register a new user with Openfire,
 * sync a user's contacts with their Openfire roster, and handle avatar
 * changes for individual user's and group chats. An intance of this class
 * should only be obtained by @sample[KtorFirebaseAuth.getKotlinFirebaseInstance]
 */
class KtorFirebaseAuth {

    private lateinit var refreshToken: InputStream
    private lateinit var firebaseOptions: FirebaseOptions
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseApp: FirebaseApp
    private val xmppServer = XMPPServer.getInstance()
    private val rosterManager = xmppServer.rosterManager
    private val userManager = xmppServer.userManager
    private val vCardManager = xmppServer.vCardManager
    private val domain = "tapinapp.com"

    /**
     * Function to verify a given Firebase ID token (passed in as a string)
     */
    fun checkToken(firebaseToken: String) :Boolean{
        var isValid = false
        try{
            firebaseAuth.verifyIdToken(firebaseToken)
            isValid = true
            //if(decodedToken.isEmailVerified) isValid = true
            KtorLogger.logText("Received valid token at contact Dump")

        }
        catch (ex: FirebaseAuthException){
            KtorLogger.logText("Unauthorized token received at ${LocalDateTime.now()} from user ")
            //println(ex.printStackTrace())

        }
        return isValid
    }

    /**
     * Function to destroy the firebase app created by the class
     */
    fun destroyFirebaseApp(){
        firebaseApp.delete()
    }

    /**
     * Inner class used to manage the Firebase user's.
     * Used to handle a given "Contact Sync" post request to
     * sync the user's phone contacts with their Openfire roster.
     */
    inner class FirebaseContactManager{

        /**
         * Given the data class [userContacts], it will
         * query all of the given phone numbers in the list [UserContacts.contactNumbers]
         * with the Firebase user's that have authenticated phone numbers. If a match is found,
         * then the matched user will be added to the user who made the request via [addToUserRoster].
         */
        fun checkFirebasePhoneNumbers(userContacts: UserContacts){
            //variable to store PhoneIdentifiers to search in Firebase Auth
            val phoneIdentifierList = mutableListOf<PhoneIdentifier>()

            //add to List, make sure phone numbers are properly formatted: +19785555555
            for(numbers in userContacts.contactNumbers){
                if(numbers.length == 10) phoneIdentifierList.add(PhoneIdentifier("+1$numbers"))
                else if (!numbers.contains('+') || numbers.length == 11){
                    phoneIdentifierList.add(PhoneIdentifier("+$numbers"))
                }
                else phoneIdentifierList.add(PhoneIdentifier(numbers))

            }
            //the result of the search by Phone Numbers
            val result = firebaseAuth.getUsersAsync(phoneIdentifierList as Collection<UserIdentifier>?).get()

            KtorLogger.logText("Successfully fetched user data:")
            for (user in result.users) {
                KtorLogger.logText(user.uid)
            }

            //add the contacts found to the user's roster
            val test = kotlin.runCatching { addToUserRoster(result.users, userContacts.userID) }
            test.onFailure { throwable ->
                KtorLogger.logText("AddToUserRoster Failed")
                KtorLogger.logTextNoDate(throwable.message.toString())
                KtorLogger.logTextNoDate(throwable.cause.toString())
                throwable.stackTrace.forEach {
                    KtorLogger.logTextNoDate(it.toString())
                }

            }


            KtorLogger.logText("Unable to find users corresponding to these identifiers:")
            for (uid in result.notFound) {
                KtorLogger.logTextNoDate(" ")
                KtorLogger.logTextNoDate("$uid")
            }
        }

        /**
         * Function used to add the given user records in [usersToAdd]
         * to the roster of user corresponding to [userID]. The [userID]
         * is the Firebase UID of a user.
         */
        private fun addToUserRoster(usersToAdd: MutableSet<UserRecord>, userID: String){
            val userRoster = rosterManager.getRoster(userID.toLowerCase())
            val userJID = JID("${userID.toLowerCase()}@$domain")
            val userDisplayName = firebaseAuth.getUser(userID).displayName


            for(users in usersToAdd){
                val contactRoster = rosterManager.getRoster(users.uid.toLowerCase())
                val checkContactRoster = runCatching{ contactRoster.getRosterItem(userJID) }
                val contactToAddJID = JID("${users.uid.toLowerCase()}@$domain")
                KtorLogger.logText("Ktor server attempting to add ${contactToAddJID.toBareJID()} to the roster of $userID")
                //ensure that the contact is not already in the user's roster
                val checkUserRoster = runCatching {userRoster.getRosterItem(contactToAddJID) }
                //if an exception is thrown, contact is not in the roster
                checkUserRoster.onFailure {
                    //JID of user to add, nickname, group to add (can be null), boolean push, boolean persistent
                    val rosterItem = userRoster.createRosterItem(contactToAddJID, users.displayName, null, true, true)
                    userRoster.updateRosterItem(rosterItem)
                    KtorLogger.logText("The contact ${contactToAddJID.toBareJID()} has been added to the roster of $userID")

                    checkContactRoster.onFailure {
                        val userRosterItem = contactRoster.createRosterItem(userJID, userDisplayName, null, true, true)
                        contactRoster.updateRosterItem(userRosterItem)
                        KtorLogger.logText("The user $userDisplayName has been added to the roster of $contactToAddJID")
                    }
                    checkContactRoster.onSuccess {
                        KtorLogger.logText("The user $userDisplayName is already a contact of the user $contactToAddJID")
                    }

                }
                //if the user was in the roster, no need to add them.
                checkUserRoster.onSuccess {
                    KtorLogger.logText("The contact ${contactToAddJID.toBareJID()} already exists in the roster for user $userID") }

            }





        }
    }

    /**
     * Inner class used to register a new user with Openfire, given
     * a valid Firebase ID token.
     * Will generate a default vcard for the user via [createNewVcard].
     *
     */
    inner class RegisterNewUer{

        /**
         * Function to verify a given firebase ID [token] passed as a string.
         * Will return a valid decoded [FirebaseToken] upon a valid ID token.
         * Otherwise this function will throw an exception [FirebaseAuthException]
         * @throws [FirebaseAuthException]
         */
        fun checkTokenNewUser(token: String) :FirebaseToken{
            return firebaseAuth.verifyIdToken(token)
        }

        /**
         * Function to register a new user with Openfire, given a valid
         * FirebaseToken via [idToken]
         */
        fun registerNewUser(idToken: FirebaseToken){
            val displayName: String = if (idToken.name.isNullOrEmpty()) firebaseAuth.getUser(idToken.uid).displayName
            else idToken.name
            //make sure the user is not already registered
            val checkIfUserExists = runCatching { userManager.getUser(idToken.uid) }
            //if an exception was thrown, user does not exist
            if(checkIfUserExists.isFailure || checkIfUserExists.getOrNull() == null){

                //the password field does not matter since my FirebaseOpenFireAuth class does not
                //store passwords in the database. So the password will be stored as null

                userManager.createUser(idToken.uid, "thisdoesnotematter", displayName, idToken.email)
                KtorLogger.logText("User ${idToken.uid} with name $displayName and email ${idToken.email} has been created")
                createNewVcard(idToken)

            }else KtorLogger.logText("The user exists or something went wrong. ${checkIfUserExists.exceptionOrNull()?.cause}")
        }

        /**
         * Function to generate a default vcard for a newly registered user given their Firebase [idToken].
         * This is needed for the client side in order to add a new contact before the new
         * user has had a chance to log in. On the client side, if a vcard does not exits, one is created.
         * This will ensure that a vcard is always created
         */
        private fun createNewVcard(idToken: FirebaseToken){
            val user = userManager.getUser(idToken.uid.toLowerCase())

            val password = CredentialManager.getDbPassword()
            val username = CredentialManager.getDbUsername()

            val newVCarDOM = "<vCard xmlns=\"vcard-temp\"><N><GIVEN>${user.name}</GIVEN></N><WEDEND>17:00</WEDEND><TUESEND>17:00</TUESEND><FRIEND>17:00</FRIEND><FN>${user.name}</FN><SATEND>17:00</SATEND><MONEND>17:00</MONEND><MONSTART>08:00</MONSTART><SUNSTART>08:00</SUNSTART><THURSTART>08:00</THURSTART><TUESSTART>08:00</TUESSTART><THUREND>17:00</THUREND><WEDSTART>08:00</WEDSTART><SATSTART>08:00</SATSTART><SUNEND>17:00</SUNEND><FRISTART>08:00</FRISTART><EMAIL><HOME/><INTERNET/><PREF/><USERID>${user.email}</USERID></EMAIL><TEL><HOME/><VOICE/><NUMBER></NUMBER></TEL><TEL><HOME/><CELL/><NUMBER></NUMBER></TEL></vCard>"

            try{
                val con: Connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/openfire", username, password
                )
                val stmt= con.prepareStatement("INSERT INTO ofVCard VALUES(?, ?)")
                stmt.setString(1, idToken.uid.toLowerCase())
                stmt.setString(2, newVCarDOM)
                stmt.executeUpdate()
                con.close()
                vCardManager.reset()
            }
            catch (exception: Exception){
                KtorLogger.logText("Something went wrong with creating a new Vcard for user ${user.name}")
            }

        }
    }

    /**
     * Inner class to manage avatar changes, either for an
     * individual user or for a group chat. Will trigger a
     * firebase message via [KtorFirebaseDb.FirebaseMessenger]
     */
    inner class AvatarNotifier {

        /**
         * Function to notify all user's about an avatar change, either a
         * user's avatar or a group avatar. The [username] is the openfire/firebase UID
         * of the user, or the group chat's JID (local part). Must signify
         * whether this change is for a group chat avatar via [isGroupAvatar].
         */
        fun notifyUsersOfAvatarChange(username: String, isGroupAvatar: Boolean){

                val avatarURL = "$secureURL/avatar/${username.substringBefore('@')}"
                if(isGroupAvatar){
                    val mucRoom = xmppServer.multiUserChatManager.getMultiUserChatService("conference").getChatRoom(
                        username.substringBefore(
                            '@'
                        )
                    )
                    mucRoom.members.forEach { memberJID ->
                        KtorFirebaseDb.FirebaseMessenger.sendUpdateAvatarMessage(
                            openFireUsername = memberJID.node,
                            avatarURL = avatarURL
                        )
                    }
                }
                else{
                    val userRoster = xmppServer.rosterManager.getRoster(username.substringBefore('@'))
                    userRoster.rosterItems.forEach { contact->
                        KtorFirebaseDb.FirebaseMessenger.sendUpdateAvatarMessage(
                            openFireUsername = contact.jid.node,
                            avatarURL = avatarURL
                        )
                    }
                }

            }
        }


    /**
     * Companion object to ensure the singleton logic for this class.
     */
    companion object{
        private const val appName = "ktorFirebaseAuth"

        @Volatile
        private var INSTANCE: KtorFirebaseAuth? = null


        /**
         * Function to get an instance of [KtorFirebaseAuth].
         * If one does not exist, it will create one, otherwise it
         * will return the already existing instance.
         */
        fun getKotlinFirebaseInstance(): KtorFirebaseAuth {
            val tempInstance = INSTANCE
            if(tempInstance != null) return tempInstance

            synchronized(this){
                val instance = KtorFirebaseAuth()
                val checkFirebaseApp = runCatching { FirebaseApp.getInstance(appName) }

                checkFirebaseApp.onFailure {
                    instance.refreshToken = CredentialManager.getResourceToken() //File("/home/colby/Desktop/JarForOpenfire/resources/tapin-c0ba6-firebase-adminsdk-wcwb2-f27fea915a.json").inputStream()
                    instance.firebaseOptions = FirebaseOptions.Builder()
                        .setCredentials(GoogleCredentials.fromStream(instance.refreshToken))
                        .setDatabaseUrl("https://tapin-c0ba6.firebaseio.com")
                        .build()
                    instance.firebaseApp = FirebaseApp.initializeApp(instance.firebaseOptions, appName)
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