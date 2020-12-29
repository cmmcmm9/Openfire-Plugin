package com.tapinapp

import java.io.InputStream
import java.util.*

/**
 * Kotlin singleton object to manage the google services JSON (stored in resources package)
 * file used to initialize a FirebaseApp. Authenticates that this project has
 * access to use my Firebase project.
 */
object CredentialManager {
    //variable to store resource stream of credential file
    private lateinit var credentialFile: InputStream

    /**
     * Function to get the google services json as a resource stream.
     * Used to initialize a Firebase App.
     */
    fun getResourceToken() :InputStream {
    credentialFile = javaClass.getResourceAsStream("/tapin-c0ba6-firebase-adminsdk-wcwb2-f27fea915a.json")
    return credentialFile
}

    /**
     * Function to close the resource stream for the google services JSON.
     * Must be done after using @see[CredentialManager.getResourceToken]
     */
    fun closeStream(){
        credentialFile.close()
    }

    fun getDbUsername() :String?{
        val properties = Properties()
        properties.load(javaClass.getResourceAsStream("/config.properties"))
        return properties.getProperty("username")
    }

    fun getDbPassword()  :String?{
        val properties = Properties()
        properties.load(javaClass.getResourceAsStream("/config.properties"))
        return properties.getProperty("password")
    }

}