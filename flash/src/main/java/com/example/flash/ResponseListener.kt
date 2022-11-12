package com.example.flash

interface ResponseListener<T> {
    fun onResponse(res: Response?)
    fun onFailure(e: Exception?)
}