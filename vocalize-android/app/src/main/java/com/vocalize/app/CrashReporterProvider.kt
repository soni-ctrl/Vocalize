package com.vocalize.app

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.vocalize.app.util.CrashReporter

class CrashReporterProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        val application = context.applicationContext as? Application
        application?.let { CrashReporter.init(it) }
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
