package com.tapinapp

import io.ktor.application.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({ main() }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("HELLO WORLD! I AM IN THE APPLICATION MODULE", response.content)
            }
        }
    }
    @Test
    fun testMyModule(){
        withTestApplication({ main() }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("HELLO WORLD! I AM IN TAPINAPP KTOR", response.content)
            }
            handleRequest(HttpMethod.Post, "/").apply{
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }
}
