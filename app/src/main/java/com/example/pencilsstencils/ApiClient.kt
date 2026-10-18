package com.example.pencilsstencils

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.Executors

data class ApiUser(val id: Int, val name: String, val email: String)
data class ApiSession(val token: String, val user: ApiUser)
data class ApiProduct(val id: Int, val name: String, val description: String, val priceCents: Int, val category: String)
data class ApiResult<T>(val value: T? = null, val error: String? = null)

/** HTTP client for the small REST API in the project's backend folder. */
object ApiClient {
    // Android emulator -> host computer. Replace with the HTTPS Azure App Service URL after deployment.
    const val BASE_URL = "http://10.0.2.2:8000/"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun register(name: String, email: String, password: String, callback: (ApiResult<ApiSession>) -> Unit) {
        val body = JSONObject().put("name", name).put("email", email).put("password", password)
        auth("api/auth/register", body, callback)
    }

    fun login(email: String, password: String, callback: (ApiResult<ApiSession>) -> Unit) {
        val body = JSONObject().put("email", email).put("password", password)
        auth("api/auth/login", body, callback)
    }

    private fun auth(path: String, body: JSONObject, callback: (ApiResult<ApiSession>) -> Unit) {
        executor.execute {
            val result = request("POST", path, body, null) { json ->
                val response = json as JSONObject
                val user = response.getJSONObject("user")
                ApiSession(response.getString("token"), ApiUser(user.getInt("id"), user.getString("name"), user.getString("email")))
            }
            mainHandler.post { callback(result) }
        }
    }

    fun products(callback: (ApiResult<List<ApiProduct>>) -> Unit) {
        executor.execute {
            val result = request("GET", "api/products", null, null) { json ->
                val array = json as JSONArray
                (0 until array.length()).map { index ->
                    val product = array.getJSONObject(index)
                    ApiProduct(product.getInt("id"), product.getString("name"), product.getString("description"), product.getInt("price_cents"), product.getString("category"))
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun logout(token: String, callback: () -> Unit = {}) {
        executor.execute {
            request("POST", "api/auth/logout", JSONObject(), token) { Unit }
            mainHandler.post(callback)
        }
    }

    private fun <T> request(method: String, path: String, body: JSONObject?, token: String?, parse: (Any) -> T): ApiResult<T> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(BASE_URL.trimEnd('/') + "/" + path.trimStart('/')).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json: Any = if (response.trimStart().startsWith("[")) JSONArray(response) else JSONObject(response.ifBlank { "{}" })
            if (status !in 200..299) {
                val errorJson=json as? JSONObject
                val message=errorJson?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: errorJson?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "API request failed ($status)"
                ApiResult(error=message)
            }
            else ApiResult(value = parse(json))
        } catch (e: Exception) {
            val problem=when(e) {
                is ConnectException -> "the server refused the connection (it may not be running)"
                is UnknownHostException -> "the API host name could not be found"
                is SocketTimeoutException -> "the API did not respond in time"
                else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            }
            ApiResult(error = "Can't reach ${BASE_URL.trimEnd('/')}. $problem. Start backend\\Start API.bat for the emulator, or set ApiClient.BASE_URL to your deployed HTTPS API address.")
        } finally {
            connection?.disconnect()
        }
    }
}
