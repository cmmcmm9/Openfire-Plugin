package com.tapinapp

import org.jivesoftware.openfire.XMPPServer
import org.jivesoftware.openfire.muc.*
import org.xmpp.packet.JID
import org.xmpp.packet.Message

/**
 * Class that handles offline messages for Group Chats (MUC).
 * Will check if every member of a group chat is present for
 * an intercepted message. If not, will trigger a firebase message
 * for the absent user to retrieve their missing group messages.
 * Note: only function that is overridden is [messageReceived]
 */
class TapInMucEventListener : MUCEventListener {

    private val xmppServer = XMPPServer.getInstance()
    private val mucChatSer = xmppServer.multiUserChatManager.getMultiUserChatService("conference")

    /**
     * Event triggered when a new room was created.
     * UNUSED
     * @param roomJID JID of the room that was created.
     */
    override fun roomCreated(roomJID: JID?) {

    }

    /**
     * Event triggered when a room was destroyed.
     * UNUSED
     * @param roomJID JID of the room that was destroyed.
     */
    override fun roomDestroyed(roomJID: JID?) {
    }

    /**
     * Event triggered when a new occupant joins a room.
     * UNUSED
     * @param roomJID the JID of the room where the occupant has joined.
     * @param user the JID of the user joining the room.
     * @param nickname nickname of the user in the room.
     */

    override fun occupantJoined(roomJID: JID?, user: JID?, nickname: String?) {
    }

    /**
     * Event triggered when an occupant changed his nickname in a room.
     *
     * @param roomJID the JID of the room where the user changed his nickname.
     * @param user the JID of the user that changed his nickname.
     * @param oldNickname old nickname of the user in the room.
     * @param newNickname new nickname of the user in the room.
     */

    override fun nicknameChanged(roomJID: JID?, user: JID?, oldNickname: String?, newNickname: String?) {
        //TODO("Not yet implemented")
    }

    /**
     * Event triggered when a room occupant sent a message to a room.
     * Will ensure that every member of the room is present for the message,
     * otherwise a Firebase message will be sent to the absent user.
     *
     * @param roomJID the JID of the room that received the message.
     * @param user the JID of the user that sent the message.
     * @param nickname nickname used by the user when sending the message.
     * @param message the message sent by the room occupant.
     */

    override fun messageReceived(roomJID: JID?, user: JID?, nickname: String?, message: Message?) {

        if(message?.body == null) return

        val mucRoom = mucChatSer.getChatRoom(roomJID?.node)
        println("The muc room is ${mucRoom.jid}")
        println("Occupants present in room number is ${mucRoom.occupantsCount}")
        val occupants = mucRoom.occupants
        var membersOfflineJID = mucRoom.members


        membersOfflineJID.forEach {
            KtorLogger.logText("The members before list is edit is $it")
        }

        occupants.forEach{onlineUser ->
            KtorLogger.logText("The occupant is ${onlineUser.nickname}")
            KtorLogger.logText("The occupant user address is ${onlineUser.userAddress.node}")
            membersOfflineJID = membersOfflineJID.filterNot { it.node == onlineUser.userAddress.node || it.node == user?.node }
        }

        membersOfflineJID.forEach {
            val openfireUserEmail = xmppServer.userManager.getUser(it.node).email
            KtorLogger.logText(" User ${it.node} is not present in the MUC")
            KtorFirebaseDb.FirebaseMessenger.sendOfflienMUCMessage(openFireUsername = it.node, email = openfireUserEmail,  messageType = mucOffline, roomJID = roomJID?.toBareJID().toString())
        }


    }

    /**
     * UNUSED
     * Event triggered when a room occupant sent a private message to another room user
     * UNUSED
     * @param toJID the JID of who the message is to.
     * @param fromJID the JID of who the message came from.
     * @param message the message sent to user.
     */

    override fun privateMessageRecieved(toJID: JID?, fromJID: JID?, message: Message?) {
    }

    /**
     * UNUSED
     * Event triggered when the subject of a room is changed.
     * UNUSED
     * @param roomJID the JID of the room that had its subject changed.
     * @param user the JID of the user that changed the subject.
     * @param newSubject new room subject.
     */

    override fun roomSubjectChanged(roomJID: JID?, user: JID?, newSubject: String?) {
    }

}