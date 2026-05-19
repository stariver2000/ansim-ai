package com.ansim.guardian.data.repository

import com.ansim.guardian.data.local.ScamCaseDao
import com.ansim.guardian.data.local.ScamCaseEntity
import com.ansim.guardian.domain.model.ScamCase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScamRepository(private val dao: ScamCaseDao) {

    private val gson = Gson()

    suspend fun searchByKeyword(keyword: String, limit: Int = 3): List<ScamCase> {
        return dao.searchByKeyword(keyword, limit).map { it.toDomain() }
    }

    suspend fun searchByCategory(category: String, limit: Int = 3): List<ScamCase> {
        return dao.searchByCategory(category, limit).map { it.toDomain() }
    }

    suspend fun getAll(): List<ScamCase> {
        return dao.getAll().map { it.toDomain() }
    }

    suspend fun count(): Int = dao.count()

    private fun ScamCaseEntity.toDomain(): ScamCase {
        val keywordList: List<String> = try {
            gson.fromJson(keywords, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            listOf()
        }
        return ScamCase(
            id = id,
            category = category,
            title = title,
            pattern = pattern,
            exampleText = exampleText,
            explanationEasy = explanationEasy,
            recommendedAction = recommendedAction,
            keywords = keywordList
        )
    }
}
