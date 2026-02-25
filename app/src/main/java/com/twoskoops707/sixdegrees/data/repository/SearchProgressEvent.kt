package com.twoskoops707.sixdegrees.data.repository

sealed class SearchProgressEvent {
    data class Checking(val source: String) : SearchProgressEvent()
    data class Found(val source: String, val detail: String) : SearchProgressEvent()
    data class NotFound(val source: String) : SearchProgressEvent()
    data class Failed(val source: String, val reason: String = "") : SearchProgressEvent()
    data class Complete(val reportId: String, val hitCount: Int) : SearchProgressEvent()
}
