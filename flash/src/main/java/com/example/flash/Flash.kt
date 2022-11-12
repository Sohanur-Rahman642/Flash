package com.example.flash

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


object Flash {
    internal const val TAG = "Flash"

    const val GET = "GET"
    const val POST = "POST"
    const val DELETE = "DELETE"
    const val PUT = "PUT"

    internal var reqTimeStamp: Long = 0


    class Request(internal val method: String) {
        private val query: MutableMap<String, String> = HashMap()
        internal val header: MutableMap<String, String> = HashMap()
        internal var uri: String? = null
        internal var body: ByteArray? = null
        private var objectResponseListener: ResponseListener<JSONObject>? = null
        private var arrayResponseListener: ResponseListener<JSONArray>? = null


        private var progressCb: ProgressCb? = null
        internal var loggingEnabled = false
        private var threadExecutor: ThreadExecutor =
            ThreadExecutor().setPriority(ThreadExecutor.DEFAULT)

        fun showLog(isLoggingEnabled: Boolean): Request {
            HttpLogger.isLogsRequired = isLoggingEnabled
            loggingEnabled = isLoggingEnabled
            return this
        }

        fun setPriority(priority: Int): Request {
            threadExecutor = ThreadExecutor().setPriority(priority)
            return this
        }

        fun url(uri: String?): Request {
            this.uri = uri
            return this
        }

        fun query(queryMap: Map<String,
                String>?): Request {
            query.putAll(queryMap!!)
            return this
        }

        fun body(json: JSONObject): Request {
            body(json.toString())
            header(CONTENT_TYPE, APPLICATION_JSON)
            return this
        }

        fun body(jsonObjectList: List<JSONObject?>): Request {
            body(jsonObjectList.toString())
            header(CONTENT_TYPE, APPLICATION_JSON)
            return this
        }

        fun body(textBody: String?): Request {
            if(textBody == null) {
                body = null
                return this
            }
            header(CONTENT_TYPE, TEXT_PLAIN)
            try {
                body = textBody.toByteArray(charset((UTF_8)))
            }catch (e: UnsupportedEncodingException) {

            }
            return this
        }

        fun header(header: Map<String, String>?): Request {
            this.header.putAll(header!!)
            return this
        }

        fun header(key: String, value: String): Request {
            header[key] = value
            return this
        }

        fun body(rawBody: ByteArray?): Request {
            if(rawBody == null) {
                body = null
                return this
            }
            body = rawBody
            return this
        }

        fun onProgress(progressCb: ProgressCb?) {
            this.progressCb = progressCb
        }

        @JvmName("executeJSONObject")
        fun execute(cb: ResponseListener<JSONObject>): Request {
            reqTimeStamp = System.currentTimeMillis()
            this.objectResponseListener = cb
            threadExecutor.execute(RequestTask(this))
            return this
        }

        fun execute(cb: ResponseListener<JSONArray>) : Request {
            reqTimeStamp = System.currentTimeMillis()
            this.arrayResponseListener = cb
            threadExecutor.execute(RequestTask(this))
            return this
        }

        internal fun fireProgress(totalRead: Int, totalAvailable: Int) {
            if (progressCb == null) return
            val percent = (totalRead.toFloat() / totalAvailable.toFloat() * 100f).toInt()
            progressCb!!.progress(this@Request, totalRead, totalAvailable, percent)
        }

        fun sendResponse(response: Response?, e: Exception?) {
            HttpLogger.d(
                TAG,
                "TIME TAKEN FOR API CALL(MILLIS) : " + (System.currentTimeMillis() - reqTimeStamp)
            )

            if (objectResponseListener != null) {
                if (e != null) objectResponseListener!!.onFailure(e)
                else objectResponseListener!!.onResponse(Response(response!!.status, response.message, response.asJSONObject()))
                return
            }
            if(arrayResponseListener != null){
                if (e != null) arrayResponseListener!!.onFailure(e)
                else arrayResponseListener!!.onResponse(Response(response!!.status, response.message, response.asJSONArray()))
                return
            }else e?.printStackTrace()
        }

        internal fun getQueryString(): String {
            if (query.isEmpty()) return ""
            val result = StringBuilder("?")
            for((key, value) in query) {
                try {
                    result.append(URLEncoder.encode(key, UTF_8))
                    result.append("=")
                    result.append(URLEncoder.encode(value, UTF_8))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return result.toString()
        }


        internal class RequestTask(private val req: Request) : Runnable {
            override fun run() {
                try {
                    val connection = request()
                    parseResponse(connection)
                }catch (e: IOException) {
                    Log.d("RequestTask run: ", e.toString())
                    req.sendResponse(null, e)
                   // e.printStackTrace()
                }
            }

            @Throws(IOException::class)
            private fun request(): HttpURLConnection {
                val url = URL(req.uri + req.getQueryString())
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = req.method
                connection.doInput = true
                if(req.loggingEnabled) {
                    HttpLogger.d(TAG, "Flash : URL : $url")
                    HttpLogger.d(TAG, "Flash : Method : ${req.method}")
                    HttpLogger.d(TAG, "Flash : Headers : " + req.header.toString())

                    if(req.body != null) HttpLogger.d(
                        TAG, "Flash : Request Body : " + HttpUtils.asString(
                            req.body
                        )
                    )
                }

                for((key, value) in req.header) {
                    connection.setRequestProperty(key, value)
                }

                if(req.body != null) {
                    connection.doInput = true
                    val os = connection.outputStream
                    os.write(req.body)
                }
                connection.connect()
                return connection
            }

            @Throws(IOException::class)
            private fun parseResponse(connection: HttpURLConnection) {
                try {
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val status = connection.responseCode
                    if (req.loggingEnabled) HttpLogger.d(
                        TAG,
                        "Flash : Response Status Code : " + status + " for URL: " + connection.url
                    )
                    val message = connection.responseMessage
                    val responseHeaders = HashMap<String?, List<String>>()
                    val headerFields: MutableMap<String?, List<String>> = HashMap(connection.headerFields)
                    responseHeaders.putAll(headerFields)
                    val validStatus = status in 200..399
                    val inputStream = if (validStatus) connection.inputStream else connection.errorStream

                    val totalAvailable = if (responseHeaders.containsKey(CONTENT_LENGTH)) responseHeaders[CONTENT_LENGTH]!![0].toInt() else -1
                    if(totalAvailable != -1) req.fireProgress(0, totalAvailable)
                    var read: Int
                    var totalRead = 0
                    val buffer = ByteArray(bufferSize)
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, read)
                        totalRead += read
                        if (totalAvailable != -1) req.fireProgress(totalRead, totalAvailable)
                    }
                    if (totalAvailable != -1) req.fireProgress(totalAvailable, totalAvailable)
                    val response = Response(byteArrayOutputStream.toByteArray(), status, message, responseHeaders)
                    if(req.loggingEnabled && !validStatus) HttpLogger.d(
                        TAG,
                        "Flash : Response Body : " + response.asString()
                    )
                    req.sendResponse(response, null)

                } catch (e: java.lang.Exception) {
                    println("Parse Exception ${e.message}")
                } finally {
                    connection.disconnect()
                }
            }
        }
        ////...Response......./////
        class Response(
            private val data: ByteArray,
            val status: Int,
            val message: String,
            val responseHeaders: Map<String?, List<String>>
        ) {
            @Throws(JSONException::class)
            fun asJSONObject(): JSONObject {
                val str = asString()
                return if (str.isEmpty()) JSONObject() else JSONObject(str)
            }

            @Throws(JSONException::class)
            fun asJSONArray(): JSONArray {
                val str = asString()
                return if (str.isEmpty()) JSONArray() else JSONArray(str)
            }

            fun asString(): String {
                return HttpUtils.asString(data)
            }
        }

    }
}