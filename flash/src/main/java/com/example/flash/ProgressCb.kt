package com.example.flash

interface ProgressCb {
    fun progress(http: Flash.Request?, totalRead: Int, totalAvailable: Int, percent: Int)
}