package reposfinder.requests

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf

private object HttpStatus {
    const val OK = 200
}

fun getRequest(url: String, token: String? = null): ResponseResultOf<ByteArray> {
    val request = Fuel.get(url)
    token?.let {
        request.header(Headers.AUTHORIZATION, "token $it")
    }
    return request.response()
}

fun postRequest(url: String, jsonBody: String? = null, token: String? = null): ResponseResultOf<ByteArray> {
    val request = Fuel.post(url)
    token?.let {
        request.header(Headers.AUTHORIZATION, "token $it")
    }
    jsonBody?.let {
        request.body(it)
    }
    return request.response()
}

fun Response.getBody(): String = this.body().asString(this.headers[Headers.CONTENT_TYPE].lastOrNull())

fun Response.isOK(): Boolean = this.statusCode == HttpStatus.OK
