package com.drdisagree.iconify.utils.overlay.manager.resource

import android.content.Context
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.Iconify.Companion.appContextLocale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.DYNAMIC_OVERLAYABLE_PACKAGES
import com.drdisagree.iconify.common.Preferences.DYNAMIC_OVERLAY_RESOURCES
import com.drdisagree.iconify.common.Preferences.DYNAMIC_OVERLAY_RESOURCES_LAND
import com.drdisagree.iconify.common.Preferences.DYNAMIC_OVERLAY_RESOURCES_NIGHT
import com.drdisagree.iconify.config.RPrefs.getString
import com.drdisagree.iconify.config.RPrefs.putString
import com.drdisagree.iconify.utils.SystemUtils.hasStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.requestStoragePermission
import com.drdisagree.iconify.utils.extension.TaskExecutor
import com.drdisagree.iconify.utils.overlay.compiler.DynamicCompiler.buildOverlay
import org.json.JSONObject
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

object ResourceManager {

    private val TAG = ResourceManager::class.java.simpleName

    fun buildOverlayWithResource(vararg resourceEntries: ResourceEntry?): Boolean {
        val hasErroredOut = AtomicBoolean(false)

        try {
            createResource(*resourceEntries.filterNotNull().toTypedArray())
        } catch (e: Exception) {
            hasErroredOut.set(true)
            Log.e(TAG, "buildOverlayWithResource:", e)
        }

        return hasErroredOut.get()
    }

    fun buildOverlayWithResource(context: Context?, vararg resourceEntries: ResourceEntry?) {
        if (!hasStoragePermission()) {
            requestStoragePermission(context!!)
        } else {
            try {
                createResource(*resourceEntries.filterNotNull().toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "buildOverlayWithResource:", e)
            }
        }
    }

    fun removeResourceFromOverlay(vararg resourceEntries: ResourceEntry?): Boolean {
        val hasErroredOut = AtomicBoolean(false)

        try {
            removeResource(*resourceEntries.filterNotNull().toTypedArray())
        } catch (e: Exception) {
            hasErroredOut.set(true)
            Log.e(TAG, "removeResourceFromOverlay:", e)
        }

        return hasErroredOut.get()
    }

    fun removeResourceFromOverlay(context: Context?, vararg resourceEntries: ResourceEntry?) {
        if (!hasStoragePermission()) {
            requestStoragePermission(context!!)
        } else {
            try {
                removeResource(*resourceEntries.filterNotNull().toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "removeResourceFromOverlay:", e)
            }
        }
    }

    @Throws(Exception::class)
    private fun createResource(vararg resourceEntries: ResourceEntry) {
        val jsonObject = resources
        val newJsonObject = generateJsonData(*resourceEntries)
        val mergedJson = Array(3) { JSONObject() }

        for (i in 0..2) {
            mergedJson[i] = initResourceIfNull(JSONObject())

            mergeJsonObjects(mergedJson[i], jsonObject[i])
            mergeJsonObjects(mergedJson[i], newJsonObject[i])
        }

        saveResources(mergedJson)

        DynamicCompilerExecutor().execute()
    }

    @Throws(Exception::class)
    private fun removeResource(vararg resourceEntries: ResourceEntry) {
        val jsonObject = resources

        for (resourceEntry in resourceEntries) {
            var index = -1

            if (resourceEntry.isPortrait()) {
                index = 0
            } else if (resourceEntry.isLandscape()) {
                index = 1
            } else if (resourceEntry.isNightMode) {
                index = 2
            }

            val resourceTypes =
                jsonObject[index].optJSONObject(resourceEntry.packageName) ?: continue
            val resources = resourceTypes.optJSONObject(resourceEntry.startEndTag) ?: continue

            resources.remove(resourceEntry.resourceName)
        }

        saveResources(jsonObject)

        DynamicCompilerExecutor().execute()
    }

    @Throws(Exception::class)
    private fun generateJsonData(vararg resourceEntries: ResourceEntry): Array<JSONObject?> {
        val packages = arrayOfNulls<JSONObject>(3)

        packages[0] = initResourceIfNull(packages[0])
        packages[1] = initResourceIfNull(packages[1])
        packages[2] = initResourceIfNull(packages[2])

        for (entry in resourceEntries) {
            if (entry.resourceValue.isEmpty()) {
                throw Exception("Resource value is empty.")
            }

            var index = -1

            if (entry.isPortrait()) {
                index = 0
            } else if (entry.isLandscape()) {
                index = 1
            } else if (entry.isNightMode) {
                index = 2
            }

            var resourceTypes = packages[index]!!.optJSONObject(entry.packageName)

            if (resourceTypes == null) {
                resourceTypes = JSONObject()
                packages[index]!!.put(entry.packageName, resourceTypes)
            }

            var resources = resourceTypes.optJSONObject(entry.startEndTag)

            if (resources == null) {
                resources = JSONObject()
                resourceTypes.put(entry.startEndTag, resources)
            }

            resources.put(entry.resourceName, entry.resourceValue)
        }

        return packages
    }

    @Throws(Exception::class)
    fun generateJsonResource(jsonObject: JSONObject): JSONObject {
        val newJsonObject = initResourceIfNull(JSONObject())
        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject[key]

            if (DYNAMIC_OVERLAYABLE_PACKAGES.contains(key)) {
                if (value is JSONObject) {
                    val xmlSerializer = Xml.newSerializer()
                    val writer = StringWriter()
                    val startEndTag = "resources"

                    xmlSerializer.setOutput(writer)
                    xmlSerializer.startDocument("UTF-8", null)
                    xmlSerializer.startTag(null, startEndTag)

                    addJsonToXml("", value, xmlSerializer)

                    xmlSerializer.endTag(null, startEndTag)
                    xmlSerializer.endDocument()

                    newJsonObject.put(key, writer.toString())
                } else {
                    throw Exception("Invalid JSON format.\n$jsonObject")
                }
            } else {
                throw Exception("Package $key is not overlayable.")
            }
        }

        return newJsonObject
    }

    @Throws(Exception::class)
    private fun addJsonToXml(parent: String, json: JSONObject, xmlSerializer: XmlSerializer) {
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = json[key]

            if (value is JSONObject) {
                addJsonToXml(key, value, xmlSerializer)
            } else {
                xmlSerializer.startTag(null, parent)
                xmlSerializer.attribute(null, "name", key)
                xmlSerializer.text(value.toString())
                xmlSerializer.endTag(null, parent)
            }
        }
    }

    @Throws(Exception::class)
    private fun mergeJsonObjects(mergedJson: JSONObject?, jsonObject: JSONObject?) {
        val keys = jsonObject!!.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject[key]

            if (value is JSONObject) {
                val existingValue = mergedJson!!.optJSONObject(key)

                if (existingValue != null) {
                    mergeJsonObjects(existingValue, value)
                } else {
                    mergedJson.put(key, value)
                }
            } else {
                mergedJson!!.put(key, value)
            }
        }
    }

    @get:Throws(Exception::class)
    val resources: Array<JSONObject>
        get() {
            val resources = getString(DYNAMIC_OVERLAY_RESOURCES, "{}") ?: "{}"
            val resourcesLand = getString(DYNAMIC_OVERLAY_RESOURCES_LAND, "{}") ?: "{}"
            val resourcesNight = getString(DYNAMIC_OVERLAY_RESOURCES_NIGHT, "{}") ?: "{}"

            val values = initResourceIfNull(resources)
            val valuesLand = initResourceIfNull(resourcesLand)
            val valuesNight = initResourceIfNull(resourcesNight)

            return arrayOf(values, valuesLand, valuesNight)
        }

    private fun saveResources(resources: Array<JSONObject>) {
        putString(DYNAMIC_OVERLAY_RESOURCES, resources[0].toString())
        putString(DYNAMIC_OVERLAY_RESOURCES_LAND, resources[1].toString())
        putString(DYNAMIC_OVERLAY_RESOURCES_NIGHT, resources[2].toString())
    }

    @Throws(Exception::class)
    private fun initResourceIfNull(json: String): JSONObject {
        return initResourceIfNull(JSONObject(json))
    }

    @Throws(Exception::class)
    private fun initResourceIfNull(jsonObject: JSONObject?): JSONObject {
        val jsonObj = jsonObject ?: JSONObject()

        for (i in DYNAMIC_OVERLAYABLE_PACKAGES.indices) {
            val packageName = DYNAMIC_OVERLAYABLE_PACKAGES[i]

            val resourceTypes = jsonObj.optJSONObject(packageName) ?: JSONObject().also {
                jsonObj.put(packageName, it)
            }

            val resources = resourceTypes.optJSONObject("color") ?: JSONObject().also {
                resourceTypes.put("color", it)
            }

            resources.put("dummy${i + 1}", "#00000000")
        }

        return jsonObj
    }

    private class DynamicCompilerExecutor : TaskExecutor<Void?, Void?, Void?>() {

        @Volatile
        var hasErroredOut = false

        override fun onPreExecute() {}

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                buildOverlay()
            } catch (e: IOException) {
                Log.i(TAG, "doInBackground: ", e)
                hasErroredOut = true
            }

            return null
        }

        override fun onPostExecute(result: Void?) {
            if (!hasErroredOut) {
                Toast.makeText(
                    appContext,
                    appContextLocale.resources.getString(R.string.toast_applied),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    appContext,
                    appContextLocale.resources.getString(R.string.toast_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
