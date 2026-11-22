package com.natantec.childrenlocation

import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

data class Link(
    val id: String,
    val parentUid: String,
    val parentEmail: String,
    val childUid: String,
    val childName: String,
    val code: String,
)

data class ChildLoc(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val battery: Int,
    val updatedMs: Long,
)

object Repo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid
    val email: String get() = auth.currentUser?.email ?: ""

    suspend fun signIn(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email.trim(), pass).await()
    }

    suspend fun signUp(email: String, pass: String) {
        auth.createUserWithEmailAndPassword(email.trim(), pass).await()
    }

    fun signOut() = auth.signOut()

    suspend fun getRole(): String? =
        db.collection("users").document(uid!!).get().await().getString("role")

    /** O papel só pode ser definido uma vez (as regras do Firestore bloqueiam alteração). */
    suspend fun setRole(role: String) {
        db.collection("users").document(uid!!).set(
            mapOf("role" to role, "email" to email, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    // ---------- Criança ----------

    /** Gera um código de 8 dígitos válido por 10 minutos. Mostrado no celular da criança. */
    suspend fun createPairingCode(): String {
        val rnd = SecureRandom()
        repeat(3) {
            val code = (10_000_000 + rnd.nextInt(90_000_000)).toString()
            try {
                db.collection("pairingCodes").document(code).set(
                    mapOf(
                        "childUid" to uid,
                        "childName" to Build.MODEL,
                        "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 10 * 60 * 1000L)),
                    )
                ).await()
                return code
            } catch (_: Exception) {
                // colisão rara: tenta outro código
            }
        }
        error("Não foi possível gerar o código. Tente de novo.")
    }

    suspend fun deletePairingCode(code: String) {
        runCatching { db.collection("pairingCodes").document(code).delete().await() }
    }

    suspend fun revokeLink(link: Link) {
        db.collection("links").document(link.id).delete().await()
    }

    suspend fun pushLocation(lat: Double, lng: Double, accuracy: Float, battery: Int) {
        val id = uid ?: return
        db.collection("locations").document(id).set(
            mapOf(
                "lat" to lat,
                "lng" to lng,
                "accuracy" to accuracy,
                "battery" to battery,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ---------- Pai / mãe ----------

    suspend fun linkWithCode(code: String) {
        val snap = db.collection("pairingCodes").document(code.trim()).get().await()
        val childUid = snap.getString("childUid") ?: error("Código inválido ou expirado")
        val name = snap.getString("childName") ?: "Filho(a)"
        val me = uid!!
        db.collection("links").document("${me}_$childUid").set(
            mapOf(
                "parentUid" to me,
                "parentEmail" to email,
                "childUid" to childUid,
                "childName" to name,
                "code" to code.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ---------- Observadores ----------

    /** field = "parentUid" (tela do responsável) ou "childUid" (tela da criança). */
    fun observeLinks(field: String): Flow<List<Link>> = callbackFlow {
        val reg = db.collection("links").whereEqualTo(field, uid)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map {
                    Link(
                        id = it.id,
                        parentUid = it.getString("parentUid") ?: "",
                        parentEmail = it.getString("parentEmail") ?: "",
                        childUid = it.getString("childUid") ?: "",
                        childName = it.getString("childName") ?: "Filho(a)",
                        code = it.getString("code") ?: "",
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeLocation(childUid: String): Flow<ChildLoc?> = callbackFlow {
        val reg = db.collection("locations").document(childUid)
            .addSnapshotListener { snap, _ ->
                val lat = snap?.getDouble("lat")
                val lng = snap?.getDouble("lng")
                if (lat == null || lng == null) {
                    trySend(null)
                } else {
                    trySend(
                        ChildLoc(
                            lat = lat,
                            lng = lng,
                            accuracy = (snap.getDouble("accuracy") ?: 0.0).toFloat(),
                            battery = (snap.getLong("battery") ?: -1L).toInt(),
                            updatedMs = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }
}
