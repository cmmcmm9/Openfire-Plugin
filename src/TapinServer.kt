package com.tapinapp


import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.serialization.DefaultJsonConfiguration
import io.ktor.serialization.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


private val kotlinFirebaseAuth = KtorFirebaseAuth.getKotlinFirebaseInstance()

/**
 * Actual Ktor server, by creating an extension function defined
 * in [Application]. This server handles all of the GET and POST requests
 * made on the client side, and will interact with Firebase and Openfire
 * based on the requests made. Is also used to serve/modify a contact avatar's
 * or group chat avatar.
 */
@Suppress("unused") // Referenced in application.conf
fun Application.main() {
    //ensure the server can handle JSON content
    install(ContentNegotiation){
        json(
            json = Json(
                DefaultJsonConfiguration.copy(
                    prettyPrint = true
                )
            ),
            contentType = ContentType.Application.Json

        )

    }

    KtorLogger.logText("Main Module Has been launches")

    /**
     * Handle POST and GET routes
     */
    routing {

        //simple test to make sure Ktor Server is up
        get("/") {
            call.respondText("HELLO WORLD! It is currently ${LocalDateTime.now().format(KtorLogger.formatter)} ", contentType = ContentType.Text.Plain)
            KtorLogger.logText("A GET CALL WAS MADE")
        }

        /**
         * GET route that will respond with the requested avatar image
         * corresponding to the [{name}] parameter. If an avatar
         * does not exist for the requested path, a [HttpStatusCode.NoContent]
         * will be issued.
         */
        get("/avatar/{name}"){
            

            val filename = call.parameters["name"]

            val debug = kotlin.runCatching {
                val file = File("/home/colby/Pictures/uploads/avatars/$filename")
                if(file.exists()){
                    call.respondFile(file)
                }
                else call.respond(HttpStatusCode.NoContent)
            }

            debug.onSuccess {
                KtorLogger.logText("Responded with Image OK")
            }

            debug.onFailure { throwable ->
                KtorLogger.logText("Failed to respond image because: ${throwable.message}")
                KtorLogger.logTextNoDate(throwable.localizedMessage)
                KtorLogger.logTextNoDate(throwable.cause.toString())
                throwable.stackTrace.forEach {
                    KtorLogger.logTextNoDate(it.toString())
                }
            }



        }

        //test POST route
        post("/testpost"){
            val message = call.receive<String>()
            KtorLogger.logText("SERVER: Message from the client: $message. In the /testpost path.")
            call.respondText("GOT INTO HERE with")

        }

        //path to register a new user
        //takes a firebase token that has been stringified on the client app

        /**
         * POST route to register a new user, given their Firebase ID token
         * in the body of the request. The Firebase ID token (JWT) should
         * be stringified on the client side.
         */
        post("/register"){
            val newUserToken = call.receive<String>()
            KtorLogger.logText("Got a firebase token request for new user")
            val checkToken = runCatching {
                kotlinFirebaseAuth.RegisterNewUer().checkTokenNewUser(newUserToken) }
            checkToken.onSuccess {
                kotlinFirebaseAuth.RegisterNewUer().registerNewUser(it)
                call.respond(HttpStatusCode.Accepted)
                KtorLogger.logText("The new user ${it.name} is registered")
            }
            checkToken.onFailure {
                call.respond(HttpStatusCode.BadRequest)
                KtorLogger.logText("Received bad request at ${LocalDate.now()} and the string was ${newUserToken.trim()}")
                KtorLogger.logText("The cause was: ${it.cause}")

            }
        }

        /**
         * POST route that will take in a [UserContacts] data class
         * in order to sync the user's contacts. The request will be rejected if
         * the [UserContacts.firebaseIDToken] is not valid. Otherwise will
         * call [KtorFirebaseAuth.FirebaseContactManager.addToUserRoster]
         */
        post("/json/tapinapp/contactdump"){
            val contactDump = call.receive<UserContacts>()
            KtorLogger.logText("The server got $contactDump")

            if(kotlinFirebaseAuth.checkToken(contactDump.firebaseIDToken)){
                call.respond(HttpStatusCode.Accepted)
                launch{
                    //openfireUCM.queryUserContacts(message)
                    kotlinFirebaseAuth.FirebaseContactManager().checkFirebasePhoneNumbers(contactDump)
                    KtorLogger.logText("Checking phone numbers")
                }
            }
            else{
                call.respond(HttpStatusCode.BadRequest)
                KtorLogger.logText("Received bad request at Contact Dump Path.")
            }

        }

        /**
         * POST route that handles a new avatar (image) upload
         * for a user or a group chat. Using multipart form data.
         */
        post("/uploads/avatar"){
            KtorLogger.logText("Got a post request in uploads path")
            var isGroupAvatar = false
            val debug = kotlin.runCatching {
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->

                    //TEXT DATA MUST BE FIRST
                    if(part is PartData.FormItem){
                        KtorLogger.logText("The part value is ${part.value}")
                        KtorLogger.logText("The part name is ${part.name}")
                        when(part.name){
                            "is-group-chat" -> isGroupAvatar = part.value == "true"
                        }
                    }

                    if(part is PartData.FileItem){

                        KtorLogger.logText("The part header is ${part.headers}")
                        KtorLogger.logText("The part name is ${part.name}")

                        when(part.name?.contains("conference")){
                            true -> isGroupAvatar = true
                        }

                        val filename = part.name?.toLowerCase().toString()

                        val storedImage = File("/home/colby/Pictures/uploads/avatars/${filename.substringBefore('@')}")

                        part.streamProvider().use { inputStream ->

                            storedImage.outputStream().buffered().use {
                                inputStream.copyTo(it)
                            }

                        }

                        try {
                            KtorFirebaseAuth.getKotlinFirebaseInstance().AvatarNotifier().notifyUsersOfAvatarChange(filename, isGroupAvatar)
                        }
                        catch (exception: Exception){
                            KtorLogger.logText("Failed to notify contacts about avatar change for user $filename")
                        }
                    }
                    part.dispose()

                }

            }

            debug.onFailure { throwable ->
                KtorLogger.logText("Failed to upload image because: ${throwable.message}")
                KtorLogger.logTextNoDate(throwable.localizedMessage)
                KtorLogger.logTextNoDate(throwable.cause.toString())
                throwable.stackTrace.forEach {
                    KtorLogger.logTextNoDate(it.toString())
                }
            }

            debug.onSuccess {
                KtorLogger.logText("Upload Succeeded")
            }

            call.respond(HttpStatusCode.Accepted)
        }

    }


}

/**
 * Kotlin object to debug and log all events
 * throughout the Ktor Server.
 * Will log to the text file ktor.log.
 */
object KtorLogger{
    private const val pathToLog = "/home/colby/Desktop/openfire/logs/ktor.log"
    val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss.SSS")!!

    /**
     * Log a message with a date and time for the message, on the same line.
     */
    fun logText(message: String){
        Files.write(File(pathToLog).toPath(), "\n${LocalDateTime.now().format(formatter)} $message".toByteArray(), StandardOpenOption.APPEND )
    }

    /**
     * Log a message without a date and time. Used mostly for stack trace output.
     */
    fun logTextNoDate(message: String){
        Files.write(File(pathToLog).toPath(), "\n$message".toByteArray(), StandardOpenOption.APPEND )
    }
}
