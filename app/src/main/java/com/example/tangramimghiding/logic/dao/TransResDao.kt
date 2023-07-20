package com.example.tangramimghiding.logic.dao

import android.util.Log
import com.example.tangramimghiding.HidingApplication
import com.google.gson.Gson
import java.io.*

object TransResDao {
    private val gson = Gson()
    private const val name = "transResCache"

    fun putTrans(blocks: Array<IntArray>){
        val path = HidingApplication.context.cacheDir.path + name
        val s = gson.toJson(blocks)
        Log.d("putTrans", "start")
        try {
            val fos = FileOutputStream(path)
            BufferedWriter(OutputStreamWriter(fos)).use {
                it.write(s)
            }
            Log.d("putTrans", "success")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d("putTrans", "fail")
        }
    }

    fun getTrans(): Array<IntArray>{
        val path = HidingApplication.context.cacheDir.path + name
        val content = StringBuilder()
        try {
            val fis = FileInputStream(path)
            BufferedReader(InputStreamReader(fis)).use { reader ->
                reader.forEachLine {
                    content.append(it)
                }
            }
        } catch (e: IOException){
            e.printStackTrace()
        }
        return gson.fromJson(content.toString(), Array<IntArray>::class.java)
    }
}