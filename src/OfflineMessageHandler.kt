package com.tapinapp

import org.jivesoftware.openfire.OfflineMessageListener
import org.jivesoftware.openfire.XMPPServer
import org.xmpp.packet.Message

/**
 * Offline Message handler called when an offline message
 * for a one-to-one chat is stored in the database. Will
 * trigger a firebase message to be sent by [KtorFirebaseDb.FirebaseMessenger]
 */
class OfflineMessageHandler : OfflineMessageListener  {
    private val xmppServer = XMPPServer.getInstance()

    /**
     * Notification message indicating that a message was not stored offline but bounced
     * back to the sender. UNUSED
     *
     * @param message the message that was bounced.
     */

    override fun messageBounced(message: Message?) {

    }

    /**
     * Notification message indicating that a message was stored offline since the target entity
     * was not online at the moment. Trigger a Firebase Message to the user
     * that the message was intended for.
     *
     * @param message the message that was stored offline.
     */

    override fun messageStored(message: Message?) {
        if(message == null || message.type != Message.Type.chat) return
        val openFireUser = xmppServer.userManager.getUser(message.to?.node)


        KtorFirebaseDb.FirebaseMessenger.sendOfflineRetrievalMessage(openFireUsername = message.to?.node.toString(), email = openFireUser.email, messageType = offlineSingle)
        KtorLogger.logText("A message was stored sent by ${message.from} and it was to ${message.to}")

    }
}