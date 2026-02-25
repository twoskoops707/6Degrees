package com.twoskoops707.sixdegrees.data.remote.dto

import com.squareup.moshi.Json

data class BaseApiResponse<T>(
    @Json(name = "status") val status: Int?,
    @Json(name = "data") val data: T?,
    @Json(name = "error") val error: String?,
    @Json(name = "message") val message: String?
)