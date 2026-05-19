package com.twoskoops707.sixdegrees.data.local

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Date

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromAddressList(value: List<com.twoskoops707.sixdegrees.domain.model.Address>): String {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Address::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Address>>(type).toJson(value)
    }

    @TypeConverter
    fun toAddressList(value: String): List<com.twoskoops707.sixdegrees.domain.model.Address> {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Address::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Address>>(type).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromEmploymentList(value: List<com.twoskoops707.sixdegrees.domain.model.Employment>): String {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Employment::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Employment>>(type).toJson(value)
    }

    @TypeConverter
    fun toEmploymentList(value: String): List<com.twoskoops707.sixdegrees.domain.model.Employment> {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.Employment::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.Employment>>(type).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromSocialProfileList(value: List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>): String {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.SocialProfile::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>>(type).toJson(value)
    }

    @TypeConverter
    fun toSocialProfileList(value: String): List<com.twoskoops707.sixdegrees.domain.model.SocialProfile> {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.SocialProfile::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.SocialProfile>>(type).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromDataSourceList(value: List<com.twoskoops707.sixdegrees.domain.model.DataSource>): String {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.DataSource::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.DataSource>>(type).toJson(value)
    }

    @TypeConverter
    fun toDataSourceList(value: String): List<com.twoskoops707.sixdegrees.domain.model.DataSource> {
        val type = Types.newParameterizedType(List::class.java, com.twoskoops707.sixdegrees.domain.model.DataSource::class.java)
        return moshi.adapter<List<com.twoskoops707.sixdegrees.domain.model.DataSource>>(type).fromJson(value) ?: emptyList()
    }
}
