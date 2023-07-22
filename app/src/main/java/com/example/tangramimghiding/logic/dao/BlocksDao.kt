package com.example.tangramimghiding.logic.dao

import com.example.tangramimghiding.HidingApplication
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter


/**
 * 用于在 Service 和 StgeoActivity 中传输分割后的图像子块
 *@author aris
 *@time 2023/7/20 22:03
*/
object BlocksDao {
    private val gson = Gson()

    fun putBlocks(name: String, blocks: IntArray){
        val path = HidingApplication.context.cacheDir.path + name
        val s = gson.toJson(blocks)
        try {
            val fos = FileOutputStream(path)
            BufferedWriter(OutputStreamWriter(fos)).use {
                it.write(s)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    fun getBlocks(name: String): IntArray{
        val path = HidingApplication.context.cacheDir.path + name
        val content = StringBuilder()
        try {
            val fis = FileInputStream(path)
            BufferedReader(InputStreamReader(fis)).use {reader ->
                reader.forEachLine {
                    content.append(it)
                }
            }
        } catch (e: IOException){
            e.printStackTrace()
        }
        return gson.fromJson(content.toString(), IntArray::class.java)
    }
}