package com.example.a6degrees.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.a6degrees.data.local.entity.CompanyEntity

@Dao
interface CompanyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompanyById(id: String): CompanyEntity?

    @Query("SELECT * FROM companies WHERE name LIKE :name OR legalName LIKE :name")
    suspend fun searchCompaniesByName(name: String): List<CompanyEntity>

    @Query("DELETE FROM companies")
    suspend fun deleteAllCompanies()
}