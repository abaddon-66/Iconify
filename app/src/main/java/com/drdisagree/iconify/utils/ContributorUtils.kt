package com.drdisagree.iconify.utils

import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.ui.models.InfoModel
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

fun parseContributors(): ArrayList<InfoModel> {
    val contributorsList = ArrayList<InfoModel>()
    val jsonStr = readJsonFileFromAssets("Misc/contributors.json")
    val jsonArray = JSONArray(jsonStr)

    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        val name = jsonObject.getString("name").replace(Regex("\\s*\\(.*\\)"), "")
        val username = jsonObject.getString("username")
        val picture = jsonObject.getString("picture")
        val languagesArray = jsonObject.getJSONArray("languages")
        val languagesList = ArrayList<String>()
        for (j in 0 until languagesArray.length()) {
            languagesList.add(languagesArray.getJSONObject(j).getString("name"))
        }
        val languages = languagesList.joinToString(", ")
        val url = "https://crowdin.com/profile/$username"

        if (username == "DrDisagree") continue

        contributorsList.add(InfoModel(name, languages, url, picture))
    }

    return contributorsList
}

fun readJsonFileFromAssets(fileName: String): String {
    val stringBuilder = StringBuilder()
    val inputStream = appContext.assets.open(fileName)
    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
    var line: String?
    while (bufferedReader.readLine().also { line = it } != null) {
        stringBuilder.append(line)
    }
    bufferedReader.close()
    return stringBuilder.toString()
}