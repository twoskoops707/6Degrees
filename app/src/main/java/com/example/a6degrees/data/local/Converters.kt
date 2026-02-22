package com.example.a6degrees.data.local

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class Converters {
    private val moshi = Moshi.Builder().build()

    // Generic converter functions
    private inline fun <reified T> toJson(value: T?): String {
        if (value == null) return "[]"
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        val adapter = moshi.adapter<List<T>>(type)
        return adapter.toJson(value)
    }

    private inline fun <reified T> fromJson(json: String): List<T> {
        if (json.isEmpty()) return emptyList()
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        val adapter = moshi.adapter<List<T>>(type)
        return adapter.fromJson(json) ?: emptyList()
    }

    // Address converters
    @TypeConverter
    fun fromAddressList(value: List<com.example.a6degrees.domain.model.Address>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Address::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Address>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toAddressList(value: String): List<com.example.a6degrees.domain.model.Address> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Address::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Address>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Employment converters
    @TypeConverter
    fun fromEmploymentList(value: List<com.example.a6degrees.domain.model.Employment>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Employment::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Employment>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toEmploymentList(value: String): List<com.example.a6degrees.domain.model.Employment> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Employment::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Employment>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // SocialProfile converters
    @TypeConverter
    fun fromSocialProfileList(value: List<com.example.a6degrees.domain.model.SocialProfile>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.SocialProfile::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.SocialProfile>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toSocialProfileList(value: String): List<com.example.a6degrees.domain.model.SocialProfile> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.SocialProfile::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.SocialProfile>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Ownership converters
    @TypeConverter
    fun fromOwnershipList(value: List<com.example.a6degrees.domain.model.Ownership>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Ownership::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Ownership>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toOwnershipList(value: String): List<com.example.a6degrees.domain.model.Ownership> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Ownership::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Ownership>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Subsidiary converters
    @TypeConverter
    fun fromSubsidiaryList(value: List<com.example.a6degrees.domain.model.Subsidiary>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Subsidiary::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Subsidiary>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toSubsidiaryList(value: String): List<com.example.a6degrees.domain.model.Subsidiary> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Subsidiary::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Subsidiary>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Technology converters
    @TypeConverter
    fun fromTechnologyList(value: List<com.example.a6degrees.domain.model.Technology>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Technology::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Technology>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toTechnologyList(value: String): List<com.example.a6degrees.domain.model.Technology> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Technology::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Technology>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Violation converters
    @TypeConverter
    fun fromViolationList(value: List<com.example.a6degrees.domain.model.Violation>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Violation::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Violation>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toViolationList(value: String): List<com.example.a6degrees.domain.model.Violation> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Violation::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Violation>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Bankruptcy converters
    @TypeConverter
    fun fromBankruptcyList(value: List<com.example.a6degrees.domain.model.Bankruptcy>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Bankruptcy::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Bankruptcy>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toBankruptcyList(value: String): List<com.example.a6degrees.domain.model.Bankruptcy> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Bankruptcy::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Bankruptcy>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Lien converters
    @TypeConverter
    fun fromLienList(value: List<com.example.a6degrees.domain.model.Lien>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Lien::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Lien>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toLienList(value: String): List<com.example.a6degrees.domain.model.Lien> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Lien::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Lien>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // Judgment converters
    @TypeConverter
    fun fromJudgmentList(value: List<com.example.a6degrees.domain.model.Judgment>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Judgment::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Judgment>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toJudgmentList(value: String): List<com.example.a6degrees.domain.model.Judgment> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.Judgment::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.Judgment>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }

    // DataSource converters
    @TypeConverter
    fun fromDataSourceList(value: List<com.example.a6degrees.domain.model.DataSource>): String {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.DataSource::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.DataSource>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toDataSourceList(value: String): List<com.example.a6degrees.domain.model.DataSource> {
        val type = Types.newParameterizedType(List::class.java, com.example.a6degrees.domain.model.DataSource::class.java)
        val adapter = moshi.adapter<List<com.example.a6degrees.domain.model.DataSource>>(type)
        return adapter.fromJson(value) ?: emptyList()
    }
}