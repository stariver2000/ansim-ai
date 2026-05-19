package com.ansim.guardian.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScamCaseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cases: List<ScamCaseEntity>)

    @Query("SELECT * FROM scam_cases")
    suspend fun getAll(): List<ScamCaseEntity>

    @Query("SELECT * FROM scam_cases WHERE category = :category")
    suspend fun getByCategory(category: String): List<ScamCaseEntity>

    // FTS 대신 LIKE 기반 키워드 검색 (1차 구현)
    // 추후 FTS5 또는 임베딩 벡터 검색으로 교체 가능하도록 DAO를 분리해둠
    @Query("""
        SELECT * FROM scam_cases
        WHERE keywords LIKE '%' || :keyword || '%'
           OR exampleText LIKE '%' || :keyword || '%'
           OR pattern LIKE '%' || :keyword || '%'
        LIMIT :limit
    """)
    suspend fun searchByKeyword(keyword: String, limit: Int = 5): List<ScamCaseEntity>

    @Query("SELECT * FROM scam_cases WHERE category = :category LIMIT :limit")
    suspend fun searchByCategory(category: String, limit: Int = 3): List<ScamCaseEntity>

    @Query("SELECT COUNT(*) FROM scam_cases")
    suspend fun count(): Int
}
