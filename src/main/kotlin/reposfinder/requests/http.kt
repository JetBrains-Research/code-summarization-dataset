package reposfinder.requests

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.ResponseResultOf

fun postRequest(url: String, token: String?, bodyJSON: String?): ResponseResultOf<ByteArray> {
    val request = Fuel.post(url)
    token?.let {
        request.header(Headers.AUTHORIZATION, "token $token")
    }
    bodyJSON?.let {
        request.body(bodyJSON)
    }
    return request.response()
}

fun getRequest(url: String, token: String?): ResponseResultOf<ByteArray> {
    val request = Fuel.get(url)
    token?.let {
        request.header(Headers.AUTHORIZATION, "token $token")
    }
    return request.response()
}
