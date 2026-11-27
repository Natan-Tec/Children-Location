package com.natantec.childrenlocation

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

object Repo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val user: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous == true
    val myEmail: String get() = (auth.currentUser?.email ?: "").trim().lowercase()

    fun signOut() = auth.signOut()

    private fun labelOf(u: FirebaseUser): String {
        val name = u.displayName?.takeIf { it.isNotBlank() } ?: (u.email ?: "Responsável").substringBefore("@")
        return "$name (${(u.email ?: "").lowercase()})"
    }

    // ---------------------------------------------------------------- Login de adulto

    suspend fun signInEmail(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email.trim(), pass).await()
    }

    suspend fun signUpEmail(email: String, pass: String) {
        val r = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
        r.user?.sendEmailVerification()?.await()
    }

    suspend fun signInGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun sendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Recarrega o usuário e o token (as regras do Firestore leem email_verified do token). */
    suspend fun refreshVerified(): Boolean {
        val u = auth.currentUser ?: return false
        u.reload().await()
        u.getIdToken(true).await()
        return u.isEmailVerified
    }

    /** Cria o perfil do adulto na primeira vez: nasce NÃO aprovado. */
    suspend fun ensureUserDoc() {
        val u = auth.currentUser ?: return
        val ref = db.collection("users").document(u.uid)
        if (ref.get().await().exists()) return
        val name = u.displayName?.takeIf { it.isNotBlank() } ?: (u.email ?: "Responsável").substringBefore("@")
        ref.set(
            mapOf(
                "email" to (u.email ?: "").trim().lowercase(),
                "name" to name,
                "aprovado" to false,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    fun observeUser(): Flow<Loaded<UserDoc>> = callbackFlow {
        val id = uid
        if (id == null) { trySend(Loaded(null)); close(); return@callbackFlow }
        val reg = db.collection("users").document(id).addSnapshotListener { s, _ ->
            if (s == null) return@addSnapshotListener
            trySend(
                Loaded(
                    if (s.exists()) UserDoc(
                        name = s.getString("name") ?: "",
                        aprovado = s.getBoolean("aprovado") ?: false,
                        bloqueado = s.getBoolean("bloqueado") ?: false,
                    ) else null
                )
            )
        }
        awaitClose { reg.remove() }
    }

    // ---------------------------------------------------------------- Família

    private fun DocumentSnapshot.toFamily(): Family {
        @Suppress("UNCHECKED_CAST")
        val names = (get("memberNames") as? Map<String, Any?>)?.mapValues { it.value?.toString() ?: "" } ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val members = (get("members") as? List<String>) ?: emptyList()
        return Family(
            id = id,
            name = getString("name") ?: "Família",
            ownerUid = getString("ownerUid") ?: "",
            members = members,
            memberNames = names,
        )
    }

    fun observeMyFamily(): Flow<Loaded<Family>> = callbackFlow {
        val id = uid
        if (id == null) { trySend(Loaded(null)); close(); return@callbackFlow }
        val reg = db.collection("families").whereArrayContains("members", id)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(Loaded(snap.documents.firstOrNull()?.toFamily()))
            }
        awaitClose { reg.remove() }
    }

    suspend fun createFamily(name: String) {
        val u = auth.currentUser ?: error("Sem login")
        db.collection("families").document().set(
            mapOf(
                "name" to name.trim(),
                "ownerUid" to u.uid,
                "members" to listOf(u.uid),
                "memberNames" to mapOf(u.uid to labelOf(u)),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun removeMember(f: Family, memberUid: String) {
        db.collection("families").document(f.id).update(
            mapOf(
                "members" to FieldValue.arrayRemove(memberUid),
                "memberNames.$memberUid" to FieldValue.delete(),
            )
        ).await()
    }

    // ---------------------------------------------------------------- Convites

    suspend fun createInvite(f: Family, email: String) {
        val u = auth.currentUser ?: error("Sem login")
        val mail = email.trim().lowercase()
        require(mail.contains("@") && mail.contains(".")) { "E-mail inválido" }
        db.collection("invites").document("${f.id}_$mail").set(
            mapOf(
                "familyId" to f.id,
                "familyName" to f.name,
                "email" to mail,
                "invitedByUid" to u.uid,
                "invitedByName" to labelOf(u),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    private fun DocumentSnapshot.toInvite() = Invite(
        id = id,
        familyId = getString("familyId") ?: "",
        familyName = getString("familyName") ?: "",
        email = getString("email") ?: "",
        invitedByName = getString("invitedByName") ?: "",
    )

    fun observeFamilyInvites(familyId: String): Flow<List<Invite>> = callbackFlow {
        val reg = db.collection("invites").whereEqualTo("familyId", familyId)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.map { it.toInvite() })
            }
        awaitClose { reg.remove() }
    }

    fun observeMyInvites(): Flow<List<Invite>> = callbackFlow {
        val mail = myEmail
        if (mail.isEmpty()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = db.collection("invites").whereEqualTo("email", mail)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.map { it.toInvite() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun acceptInvite(inv: Invite) {
        val u = auth.currentUser ?: error("Sem login")
        val batch = db.batch()
        batch.update(
            db.collection("families").document(inv.familyId),
            mapOf(
                "members" to FieldValue.arrayUnion(u.uid),
                "memberNames.${u.uid}" to labelOf(u),
            )
        )
        batch.delete(db.collection("invites").document(inv.id))
        batch.commit().await()
    }

    suspend fun deleteInvite(inv: Invite) {
        db.collection("invites").document(inv.id).delete().await()
    }

    // ---------------------------------------------------------------- Crianças (lado dos pais)

    private fun DocumentSnapshot.toChild(familyId: String) = Child(
        id = id,
        familyId = familyId,
        name = getString("name") ?: "",
        emoji = getString("emoji") ?: "🙂",
        deviceUid = getString("deviceUid"),
    )

    fun observeChildren(f: Family): Flow<List<Child>> = callbackFlow {
        val reg = db.collection("families").document(f.id).collection("children")
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.map { it.toChild(f.id) }.sortedBy { it.name.lowercase() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun addChild(f: Family, name: String, emoji: String): Child {
        val ref = db.collection("families").document(f.id).collection("children").document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "emoji" to emoji,
                "familyName" to f.name,
                "deviceUid" to null,
                "pairedAt" to null,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        return Child(ref.id, f.id, name.trim(), emoji, null)
    }

    suspend fun deleteChild(c: Child) {
        db.collection("families").document(c.familyId).collection("children").document(c.id).delete().await()
    }

    /** Solta o aparelho vinculado (a criança volta para a tela de escanear QR). */
    suspend fun unpairChild(c: Child) {
        db.collection("families").document(c.familyId).collection("children").document(c.id)
            .update(mapOf("deviceUid" to null, "pairedAt" to null)).await()
    }

    /** Gera o conteúdo do QR: token aleatório de 24 caracteres, válido por 15 minutos. */
    suspend fun createPairQr(c: Child): String {
        val u = auth.currentUser ?: error("Sem login")
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        val token = (1..24).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        db.collection("pairTokens").document(token).set(
            mapOf(
                "familyId" to c.familyId,
                "childId" to c.id,
                "createdBy" to u.uid,
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 15 * 60 * 1000L)),
            )
        ).await()
        return "CLOC1|${c.familyId}|${c.id}|$token"
    }

    // ---------------------------------------------------------------- Criança (aparelho sem conta)

    /** Lê o QR, entra de forma anônima e assume o perfil da criança. */
    suspend fun claimPairing(qr: String): Pairing {
        val p = qr.trim().split("|")
        require(p.size == 4 && p[0] == "CLOC1") { "QR code inválido" }
        val (familyId, childId, token) = Triple(p[1], p[2], p[3])
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val me = auth.currentUser!!.uid
        val childRef = db.collection("families").document(familyId).collection("children").document(childId)
        val batch = db.batch()
        batch.update(
            childRef,
            mapOf("deviceUid" to me, "pairedAt" to FieldValue.serverTimestamp(), "pairToken" to token)
        )
        batch.delete(db.collection("pairTokens").document(token))
        try {
            batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
            throw IllegalStateException("QR expirado ou já usado. Peça um novo aos seus pais.")
        }
        val snap = childRef.get().await()
        return Pairing(
            familyId = familyId,
            childId = childId,
            childName = snap.getString("name") ?: "",
            familyName = snap.getString("familyName") ?: "",
        )
    }

    /**
     * true = continua pareado; false = os pais soltaram o aparelho (ou apagaram o perfil).
     * Quando o vínculo é desfeito, a regra de leitura passa a negar e o Firestore devolve
     * PERMISSION_DENIED: é assim que o aparelho percebe.
     */
    fun observePaired(p: Pairing): Flow<Boolean> = callbackFlow {
        val me = uid
        val ref = db.collection("families").document(p.familyId).collection("children").document(p.childId)
        val reg = ref.addSnapshotListener { s, e ->
            if (e != null) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) trySend(false)
                return@addSnapshotListener
            }
            if (s != null) trySend(s.exists() && s.getString("deviceUid") == me)
        }
        awaitClose { reg.remove() }
    }

    // ---------------------------------------------------------------- Localização

    suspend fun pushLocation(p: Pairing, lat: Double, lng: Double, accuracy: Float, battery: Int) {
        db.collection("families").document(p.familyId).collection("children").document(p.childId)
            .collection("live").document("current").set(
                mapOf(
                    "lat" to lat,
                    "lng" to lng,
                    "accuracy" to accuracy,
                    "battery" to battery,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
    }

    fun observeLocation(familyId: String, childId: String): Flow<ChildLoc?> = callbackFlow {
        val reg = db.collection("families").document(familyId).collection("children").document(childId)
            .collection("live").document("current")
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
                            updatedMs = snap.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    )
                }
            }
        awaitClose { reg.remove() }
    }
}
