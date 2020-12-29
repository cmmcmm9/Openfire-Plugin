package com.tapinapp

import org.dom4j.Element
import org.jivesoftware.openfire.XMPPServer
import org.jivesoftware.openfire.vcard.VCardListener

/**
 * Class that handles a contact updating their Vcard.
 * Will trigger a Firebase Message via [KtorFirebaseDb.FirebaseMessenger]
 * to all of the user's contacts to signify they must retrieve the new contact
 * information.
 * Note:
 * All overridden function trigger the [notifyVCarUpdate]
 * function.
 */
class VCardUpdateEventListener :VCardListener {

    private val xmppServer = XMPPServer.getInstance()
    private val userManager = xmppServer.userManager

    /**
     * All overridden function trigger the [notifyVCarUpdate]
     * function.
     */
    override fun vCardCreated(username: String?, vCard: Element?) {
        notifyVCarUpdate(username)
    }

    /**
     * All overridden function trigger the [notifyVCarUpdate]
     * function.
     */
    override fun vCardUpdated(username: String?, vCard: Element?) {
        notifyVCarUpdate(username)
    }

    /**
     * All overridden function trigger the [notifyVCarUpdate]
     * function.
     */
    override fun vCardDeleted(username: String?, vCard: Element?) {
        notifyVCarUpdate(username)
    }

    /**
     * Function to notify all contacts of [username]
     * that they must retrieve the new user information
     * via [KtorFirebaseDb.FirebaseMessenger].
     */
    private fun notifyVCarUpdate(username: String?){
        val getUser = kotlin.runCatching { userManager.getUser(username) }

        getUser.onSuccess { user ->
            KtorLogger.logText("User ${user.username} updated their vcard")

            user.roster.rosterItems.forEach{
                KtorLogger.logText("Notifying ${it.jid.node} about user ${user.username}'s Vcard change")
                val contact = userManager.getUser(it.jid.node)
                KtorFirebaseDb.FirebaseMessenger.sendVcardUpdateMessage(openFireUsername = it.jid.node , email = contact.email, messageType = vcardUpdated, contactJID = "${user.username}@$domain" )
            }

        }
        getUser.onFailure { throwable ->
            KtorLogger.logText("Could not get user $username")
            KtorLogger.logTextNoDate(throwable.localizedMessage)
            throwable.stackTrace.forEach {
                KtorLogger.logTextNoDate(it.toString())
            }
        }
    }
}
