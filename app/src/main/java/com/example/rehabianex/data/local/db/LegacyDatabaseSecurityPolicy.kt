package com.example.rehabianex.data.local.db

enum class LegacyDatabaseAction {
    Continue,
    DeletePlaintextDebugDatabase,
    RejectPlaintextReleaseDatabase,
}

fun legacyDatabaseAction(
    databaseExists: Boolean,
    hasPlaintextHeader: Boolean,
    isDebug: Boolean,
): LegacyDatabaseAction = when {
    !databaseExists || !hasPlaintextHeader -> LegacyDatabaseAction.Continue
    isDebug -> LegacyDatabaseAction.DeletePlaintextDebugDatabase
    else -> LegacyDatabaseAction.RejectPlaintextReleaseDatabase
}
