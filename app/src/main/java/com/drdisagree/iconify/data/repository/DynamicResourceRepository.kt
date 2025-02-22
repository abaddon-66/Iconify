package com.drdisagree.iconify.data.repository

import com.drdisagree.iconify.data.dao.DynamicResourceDao
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.entity.DynamicResourceEntity

class DynamicResourceRepository(
    private val dynamicResourceDao: DynamicResourceDao = DynamicResourceDatabase
        .getInstance()
        .dynamicResourceDao()
) {

    suspend fun insertResources(resources: List<DynamicResourceEntity>) {
        dynamicResourceDao.insertResources(resources)
    }

    suspend fun deleteResources(resources: List<DynamicResourceEntity>) {
        val packageNames = resources.map { it.packageName }
        val startEndTags = resources.map { it.startEndTag }
        val resourceNames = resources.map { it.resourceName }
        val resourceValues = resources.map { it.resourceValue }
        val isPortraits = resources.map { it.isPortrait }
        val isLandscapes = resources.map { it.isLandscape }
        val isNightModes = resources.map { it.isNightMode }

        dynamicResourceDao.deleteResources(
            packageNames,
            startEndTags,
            resourceNames,
            resourceValues,
            isPortraits,
            isLandscapes,
            isNightModes
        )
    }

    suspend fun getAllResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getAllResources()
    }

    suspend fun getResourcesForPackage(packageName: String): List<DynamicResourceEntity> {
        return dynamicResourceDao.getResourcesForPackage(packageName)
    }

    suspend fun deleteResourcesForPackage(packageName: String) {
        dynamicResourceDao.deleteResourcesForPackage(packageName)
    }

    suspend fun getPortraitResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getPortraitResources()
    }

    suspend fun getLandscapeResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getLandscapeResources()
    }

    suspend fun getNightModeResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getNightModeResources()
    }
}