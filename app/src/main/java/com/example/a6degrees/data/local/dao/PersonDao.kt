package com.example.a6degrees.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.a6degrees.data.local.entity.PersonEntity

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getPersonById(id: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE fullName LIKE :name OR firstName LIKE :name OR lastName LIKE :name")
    suspend fun searchPersonsByName(name: String): List<PersonEntity>

    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()
}