package com.vegamstories.app.data

import com.vegamstories.app.data.remote.logging.ApiCallCountInterceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.IOException

/** Debug builds count every call to the server, named after the Retrofit function that made it. */
class ApiCallCountInterceptorTest {

    private interface TestApi {
        @GET("stories") fun getStories(): Call<ResponseBody>
        @POST("rpc/set_story_like") fun setStoryLike(): Call<ResponseBody>
    }

    private val counted = mutableListOf<String>()
    private var serverDown = false

    private val client = OkHttpClient.Builder()
        .addInterceptor(ApiCallCountInterceptor { counted += it })
        // Stands in for the server, so nothing goes over the network.
        .addInterceptor { chain ->
            if (serverDown) throw IOException("no network")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://example.com/rest/v1/")
        .client(client)
        .build()
        .create(TestApi::class.java)

    @Test
    fun `each call counts under the name of its Retrofit function`() {
        api.getStories().execute()
        api.setStoryLike().execute()
        api.getStories().execute()

        assertEquals(listOf("getStories", "setStoryLike", "getStories"), counted)
    }

    @Test
    fun `a call that gets no answer still counts`() {
        serverDown = true
        runCatching { api.getStories().execute() }

        assertEquals(listOf("getStories"), counted)
    }

    @Test
    fun `a call made without Retrofit is named by its method and file`() {
        client.newCall(Request.Builder().url("https://example.com/catalogue/v1.json").build()).execute()

        assertEquals(listOf("GET v1.json"), counted)
    }
}
