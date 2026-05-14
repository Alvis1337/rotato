package com.chrisalvis.rotato.worker

import android.app.backup.BackupAgentHelper
import android.app.backup.FileBackupHelper
import android.content.Context

class RotatoBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        val prefs = getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ENABLED, true)) {
            addHelper("datastore", FileBackupHelper(this, DATASTORE_FILE))
        }
    }

    companion object {
        const val BACKUP_PREFS = "rotato_backup_cfg"
        const val KEY_ENABLED = "google_drive_backup_enabled"
        private const val DATASTORE_FILE = "../datastore/rotato_prefs.preferences_pb"
    }
}
