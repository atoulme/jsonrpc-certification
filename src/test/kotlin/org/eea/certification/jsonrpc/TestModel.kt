package org.eea.certification.jsonrpc

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TestModel @JsonCreator constructor(@JsonProperty("request") val request: TestModelRequest, @JsonProperty("response") val response: TestModelResponse)

data class TestModelRequest @JsonCreator constructor(@JsonProperty("params") val params: List<Any>, @JsonProperty("method") val method: String)

data class TestModelResponse @JsonCreator constructor(@JsonProperty("result") val result: Any)
