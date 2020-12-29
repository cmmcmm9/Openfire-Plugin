package com.tapinapp

import kotlinx.serialization.Serializable

/**
 * Data class used to sync a user's contacts
 * stored on their phone with their Openfire roster.
 * Used in POST route "/json/tapinapp/contactdump".
 * Marked serializable so that the data class can be
 * converted to a JSON and vice versa.
 */
@Serializable
data class UserContacts(
    val userID: String,
    val firebaseIDToken: String,
    val contactNumbers: List<String>
)