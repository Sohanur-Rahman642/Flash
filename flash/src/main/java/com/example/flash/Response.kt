package com.example.flash

import org.json.JSONObject

 class Response(code:Int,
                message: String,
                body: Any){

      var code: Int
      var message: String
      var body: Any

    init {
        this.code = code
        this.message = message
        this.body = body
    }


 }