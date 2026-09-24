package com.yashvant.shardwave.data

import com.yashvant.shardwave.ui.main.ModelItem
import com.yashvant.shardwave.ui.main.ModelStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local JSON persistence for storing user's model catalog across app launches.
 */
class ModelDatabase(private val dbFile: File) {

    fun loadModels(defaultCatalog: List<ModelItem>): List<ModelItem> {
        if (!dbFile.exists()) return defaultCatalog
        return try {
            val jsonStr = dbFile.readText()
            val array = JSONArray(jsonStr)
            val result = mutableListOf<ModelItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", "CATALOG")
                val status = try { ModelStatus.valueOf(statusStr) } catch (e: Exception) { ModelStatus.CATALOG }

                result.add(
                    ModelItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        version = obj.optInt("version", 1),
                        sizeMb = obj.optInt("sizeMb", 400),
                        status = status,
                        downloadUrl = obj.optString("downloadUrl", "")
                    )
                )
            }
            if (result.isEmpty()) defaultCatalog else result
        } catch (e: Exception) {
            defaultCatalog
        }
    }

    fun saveModels(models: List<ModelItem>) {
        try {
            dbFile.parentFile?.mkdirs()
            val array = JSONArray()
            for (m in models) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("name", m.name)
                obj.put("version", m.version)
                obj.put("sizeMb", m.sizeMb)
                obj.put("status", m.status.name)
                obj.put("downloadUrl", m.downloadUrl ?: "")
                array.put(obj)
            }
            dbFile.writeText(array.toString(2))
        } catch (ignored: Exception) {
        }
    }
}
