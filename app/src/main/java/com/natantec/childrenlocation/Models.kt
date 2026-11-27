package com.natantec.childrenlocation

import android.content.Context

/** Usado para diferenciar "carregando" (null) de "carregado, mas vazio" (Loaded(null)). */
data class Loaded<T>(val value: T?)

data class UserDoc(
    val name: String,
    val aprovado: Boolean,
    val bloqueado: Boolean,
)

data class Family(
    val id: String,
    val name: String,
    val ownerUid: String,
    val members: List<String>,
    val memberNames: Map<String, String>,
)

data class Child(
    val id: String,
    val familyId: String,
    val name: String,
    val emoji: String,
    val deviceUid: String?,
) {
    val paired: Boolean get() = !deviceUid.isNullOrEmpty()
}

data class Invite(
    val id: String,
    val familyId: String,
    val familyName: String,
    val email: String,
    val invitedByName: String,
)

data class ChildLoc(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val battery: Int,
    val updatedMs: Long,
)

/** Dados guardados no aparelho da criança depois de escanear o QR. */
data class Pairing(
    val familyId: String,
    val childId: String,
    val childName: String,
    val familyName: String,
)

object Prefs {
    private const val FILE = "cl_prefs"

    fun savePairing(ctx: Context, p: Pairing) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("familyId", p.familyId)
            .putString("childId", p.childId)
            .putString("childName", p.childName)
            .putString("familyName", p.familyName)
            .apply()
    }

    fun loadPairing(ctx: Context): Pairing? {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val f = sp.getString("familyId", null) ?: return null
        val c = sp.getString("childId", null) ?: return null
        return Pairing(f, c, sp.getString("childName", "") ?: "", sp.getString("familyName", "") ?: "")
    }

    fun clearPairing(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove("familyId").remove("childId").remove("childName").remove("familyName")
            .apply()
    }

    fun batteryPromptShown(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("batteryPrompt", false)

    fun setBatteryPromptShown(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("batteryPrompt", true).apply()
    }
}
