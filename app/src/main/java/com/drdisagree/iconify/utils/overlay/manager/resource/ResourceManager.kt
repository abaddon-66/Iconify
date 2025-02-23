package com.drdisagree.iconify.utils.overlay.manager.resource

import android.util.Log
import android.widget.Toast
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.Iconify.Companion.appContextLocale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.DYNAMIC_OVERLAYABLE_PACKAGES
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.entity.DynamicResourceEntity
import com.drdisagree.iconify.data.repository.DynamicResourceRepository
import com.drdisagree.iconify.utils.SystemUtils.hasStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.requestStoragePermission
import com.drdisagree.iconify.utils.extension.TaskExecutor
import com.drdisagree.iconify.utils.overlay.compiler.DynamicCompiler.buildOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

object ResourceManager {

    private val TAG = ResourceManager::class.java.simpleName
    private val repository: DynamicResourceRepository
        get() = DynamicResourceRepository(
            DynamicResourceDatabase.getInstance().dynamicResourceDao()
        )

    suspend fun buildOverlayWithResource(vararg resourceEntries: ResourceEntry?): Boolean {
        if (!hasStoragePermission()) {
            requestStoragePermission(appContext)
        } else {
            val hasErroredOut = AtomicBoolean(false)

            try {
                withContext(Dispatchers.IO) {
                    saveResources(*resourceEntries.filterNotNull().toTypedArray())
                }
            } catch (e: Exception) {
                hasErroredOut.set(true)
                Log.e(TAG, "buildOverlayWithResource:", e)
            }

            return hasErroredOut.get()
        }

        return true
    }

    suspend fun removeResourceFromOverlay(vararg resourceEntries: ResourceEntry?): Boolean {
        if (!hasStoragePermission()) {
            requestStoragePermission(appContext)
        } else {
            val hasErroredOut = AtomicBoolean(false)

            try {
                withContext(Dispatchers.IO) {
                    removeResources(*resourceEntries.filterNotNull().toTypedArray())
                }
            } catch (e: Exception) {
                hasErroredOut.set(true)
                Log.e(TAG, "removeResourceFromOverlay:", e)
            }

            return hasErroredOut.get()
        }

        return true
    }

    private suspend fun saveResources(vararg resourceEntries: ResourceEntry) {
        withContext(Dispatchers.IO) {
            repository.insertResources(
                resourceEntries.map { entry ->
                    DynamicResourceEntity(
                        packageName = entry.packageName,
                        startEndTag = entry.startEndTag,
                        resourceName = entry.resourceName,
                        resourceValue = entry.resourceValue,
                        isPortrait = entry.isPortrait,
                        isLandscape = entry.isLandscape,
                        isNightMode = entry.isNightMode,
                    )
                }
            )
        }

        DynamicCompilerExecutor().execute()
    }

    private suspend fun removeResources(vararg resourceEntries: ResourceEntry) {
        withContext(Dispatchers.IO) {
            repository.deleteResources(
                resourceEntries.map { entry ->
                    DynamicResourceEntity(
                        packageName = entry.packageName,
                        startEndTag = entry.startEndTag,
                        resourceName = entry.resourceName,
                        resourceValue = entry.resourceValue,
                        isPortrait = entry.isPortrait,
                        isLandscape = entry.isLandscape,
                        isNightMode = entry.isNightMode,
                    )
                }
            )
        }

        DynamicCompilerExecutor().execute()
    }

    suspend fun generateXmlStructureForAllResources(): MutableMap<String, MutableMap<String, ArrayList<String>>> {
        return withContext(Dispatchers.IO) {
            val resourceEntries = repository.getAllResources()
            val resourcesGrouped = resourceEntries.groupBy { it.packageName }
            val result: MutableMap<String, MutableMap<String, ArrayList<String>>> = mutableMapOf()
            val emptyResources = getEmptyResources()

            resourcesGrouped.forEach { (packageName, resources) ->
                if (!DYNAMIC_OVERLAYABLE_PACKAGES.contains(packageName)) {
                    throw Exception("Package $packageName is not dynamically overlayable.")
                }

                val xmlPartsMap: MutableMap<String, ArrayList<String>> = mutableMapOf()

                val groupedByType = resources.groupBy {
                    when {
                        it.isPortrait -> "portrait"
                        it.isLandscape -> "landscape"
                        it.isNightMode -> "night"
                        else -> throw Exception("Invalid resource type")
                    }
                }

                groupedByType.forEach { (type, group) ->
                    val xmlParts = ArrayList<String>()

                    xmlParts.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    xmlParts.add("<resources>")
                    group.forEach { entry ->
                        xmlParts.add("<${entry.startEndTag} name=\"${entry.resourceName}\">${entry.resourceValue}</${entry.startEndTag}>")
                    }
                    xmlParts.add("</resources>")

                    xmlPartsMap[type] = xmlParts
                }

                if (xmlPartsMap["portrait"].isNullOrEmpty()) {
                    xmlPartsMap["portrait"] = emptyResources[packageName]!!
                }

                result[packageName] = xmlPartsMap
            }

            DYNAMIC_OVERLAYABLE_PACKAGES.forEach { packageName ->
                if (!result.containsKey(packageName)) {
                    val xmlPartsMap: MutableMap<String, ArrayList<String>> = mutableMapOf()
                    xmlPartsMap["portrait"] = emptyResources[packageName]!!
                    result[packageName] = xmlPartsMap
                }
            }

            result
        }
    }

    private fun getEmptyResources(): MutableMap<String, ArrayList<String>> {
        val result: MutableMap<String, ArrayList<String>> = mutableMapOf()

        for (i in DYNAMIC_OVERLAYABLE_PACKAGES.indices) {
            result[DYNAMIC_OVERLAYABLE_PACKAGES[i]] = ArrayList<String>().apply {
                add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                add("<resources>")
                add("<color name=\"dummy${i + 1}\">#00000000</color>")
                add("</resources>")
            }
        }

        return result
    }

    private class DynamicCompilerExecutor : TaskExecutor<Void?, Void?, Void?>() {

        @Volatile
        var hasErroredOut = false

        override fun onPreExecute() {}

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                runBlocking {
                    buildOverlay()
                }
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
