package org.eea.certification.jsonrpc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.tuweni.eth.EthJsonModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class JsonRpcTest {

    companion object {

        lateinit var httpClient: CloseableHttpClient
        val mapper = ObjectMapper()
        val yamlMapper = ObjectMapper(YAMLFactory())

        var serverEndpoint: String = ""

        @JvmStatic
        @BeforeAll
        fun setUp() {
            httpClient = HttpClients.createDefault()
            mapper.registerModule(EthJsonModule())
            yamlMapper.registerModule(EthJsonModule())
            serverEndpoint = System.getenv()["SERVER_ENDPOINT"]
                ?: "localhost"

        }

        @JvmStatic
        fun allRequests(): Stream<Arguments> {
            val testData = JsonRpcTest::class.java.getResourceAsStream("/tests.yaml")
            val testDataType = object : TypeReference<HashMap<String, TestModel>>() {}
            val tests = yamlMapper.readValue(testData, testDataType)

            var counter = 9
            return tests.map {
                counter++
                Arguments.of(
                    it.key,
                    mapOf(
                        Pair("id", counter),
                        Pair("jsonrpc", "2.0"),
                        Pair("params", it.value.request.params),
                        Pair("method", it.value.request.method)
                    ),
                    mapOf(
                        Pair("jsonrpc", "2.0"),
                        Pair("id", counter),
                        Pair("result", it.value.response.result)
                    )
                )
            }.stream()
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequest(method: String, requestBody: Map<String, Any>, expectedResponse: Map<String, Any>) {
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        val strBody = mapper.writeValueAsString(requestBody)
        println(strBody)
        request.entity = StringEntity(strBody)
        val response = httpClient.execute(request)
        response.use {

            val bytes = response.entity.content.readAllBytes()
            val responseType = object : TypeReference<HashMap<String, Any>>() {}
            val responseBody = mapper.readValue(bytes.toString(Charsets.UTF_8), responseType)

            for (entry in expectedResponse.entries) {
                assertEquals(entry.value, responseBody[entry.key], String(bytes))
            }
            assertEquals(expectedResponse.size, responseBody.size, String(bytes))
            assertEquals(200, response.statusLine.statusCode)
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequestMissingId(method: String, requestBody: MutableMap<String, Any>) {
        requestBody.remove("id")
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(mapper.writeValueAsString(requestBody))
        val response = httpClient.execute(request)
        response.use {
            assertEquals(200, response.statusLine.statusCode)
            val bytes = response.entity.content.readAllBytes()
            assertEquals("", String(bytes))
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequestMissingParams(
        method: String,
        requestBody: MutableMap<String, Any>,
        expectedResponse: Map<String, Any>
    ) {
        requestBody.remove("params")
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(mapper.writeValueAsString(requestBody))
        val response = httpClient.execute(request)
        response.use {
            assertEquals(200, response.statusLine.statusCode)
            val bytes = response.entity.content.readAllBytes()
            val responseType = object : TypeReference<HashMap<String, Any>>() {}
            val responseBody = mapper.readValue(bytes.toString(Charsets.UTF_8), responseType)
            for (entry in expectedResponse.entries) {
                assertEquals(entry.value, responseBody[entry.key], bytes.toString())
            }
            assertEquals(expectedResponse.size, responseBody.size, bytes.toString())
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequestMissingJsonrpc(
        method: String,
        requestBody: MutableMap<String, Any>,
        expectedResponse: Map<String, Any>
    ) {
        requestBody.remove("jsonrpc")
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(mapper.writeValueAsString(requestBody))
        val response = httpClient.execute(request)
        response.use {
            assertEquals(200, response.statusLine.statusCode)
            val bytes = response.entity.content.readAllBytes()
            val responseType = object : TypeReference<HashMap<String, Any>>() {}
            val responseBody = mapper.readValue(bytes.toString(Charsets.UTF_8), responseType)
            for (entry in expectedResponse.entries) {
                assertEquals(entry.value, responseBody[entry.key], response.toString())
            }
            assertEquals(expectedResponse.size, responseBody.size, response.toString())
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequestMissingJsonContentType(
        method: String,
        requestBody: MutableMap<String, Any>,
        expectedResponse: Map<String, Any>
    ) {
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(mapper.writeValueAsString(requestBody))
        val response = httpClient.execute(request)
        response.use {
            assertEquals(200, response.statusLine.statusCode)
            val bytes = response.entity.content.readAllBytes()
            assertEquals("invalid content type, only application/json is supported", String(bytes))
        }
    }

    @MethodSource("allRequests")
    @ParameterizedTest(name = "{0}")
    fun testRequestMissingMethod(method: String, requestBody: MutableMap<String, Any>) {
        val expectedResponse = mapOf(
            Pair("id", requestBody["id"]),
            Pair("jsonrpc", "2.0"),
            Pair("error", mapOf(Pair("code", -32600), Pair("message", "Invalid Request")))
        )
        requestBody.remove("method")
        val request = HttpPost("http://${serverEndpoint}:8545")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(mapper.writeValueAsString(requestBody))
        val response = httpClient.execute(request)
        response.use {
            assertEquals(400, response.statusLine.statusCode)
            val bytes = response.entity.content.readAllBytes()
            val responseType = object : TypeReference<HashMap<String, Any>>() {}
            val responseBody = mapper.readValue(bytes.toString(Charsets.UTF_8), responseType)
            for (entry in expectedResponse.entries) {
                assertEquals(entry.value, responseBody[entry.key], responseBody.toString())
            }
            assertEquals(expectedResponse.size, responseBody.size, responseBody.toString())
        }
    }


}