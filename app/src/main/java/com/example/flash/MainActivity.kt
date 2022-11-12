package com.example.flash

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private lateinit var showResponseText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        showResponseText = findViewById(R.id.showResponseTxt)


        CoroutineScope(Dispatchers.IO).launch {
            Flash.Request(Flash.GET)
                .url("https://jsonplaceholder.typicode.com/users")
                .showLog(true)
                .execute(object : ResponseListener<JSONArray> {
                    override fun onResponse(res: Response?) {
                        if(res?.code == 200) {
                            Log.d("onResponse: ", res.body.toString())
                            runOnUiThread(Runnable {
                                showResponseText.text = res.body.toString()
                            })
                        }
                    }

                    override fun onFailure(e: Exception?) {
                        Log.d("onFailure: ", e.toString())
                    }
                })
        }

    }
}