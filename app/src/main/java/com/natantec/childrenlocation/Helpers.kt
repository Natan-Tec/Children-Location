package com.natantec.childrenlocation

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Desenha o QR code como imagem. */
fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

/** Leitor de QR da Google (não pede permissão de câmera). */
fun scanQr(ctx: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { b -> onResult(b.rawValue ?: "") }
        .addOnCanceledListener { onError("Leitura cancelada") }
        .addOnFailureListener { e -> onError(e.message ?: "Não foi possível ler o QR code") }
}

/** Pede ao Android o token da conta Google (o ID do cliente vem do google-services.json). */
suspend fun googleIdToken(activity: Activity): String {
    val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    require(resId != 0) { "Login com Google ainda não foi configurado no Firebase" }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(activity.getString(resId))
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(activity).getCredential(activity, request)
    val cred = result.credential
    require(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Resposta inesperada do login com Google"
    }
    return GoogleIdTokenCredential.createFrom(cred.data).idToken
}

/** Mapa OpenStreetMap com um marcador (sem chave de API). */
@Composable
fun OsmMap(modifier: Modifier = Modifier.fillMaxWidth(), lat: Double?, lng: Double?, title: String) {
    val ctx = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            map.overlays.clear()
            if (lat != null && lng != null) {
                val p = GeoPoint(lat, lng)
                map.overlays.add(Marker(map).apply {
                    position = p
                    this.title = title
                })
                map.controller.animateTo(p)
            }
            map.invalidate()
        }
    )
}

/** collectAsState começando em "carregando" (null). */
@Composable
fun <T> Flow<T>.collectOrNull(): State<T?> = collectAsState(initial = null)

fun granted(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Mensagens de erro em português. */
fun friendlyError(e: Throwable): String {
    if (e is FirebaseNetworkException) return "Sem internet. Verifique a conexão e tente de novo."
    if (e is FirebaseAuthException) {
        return when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "E-mail inválido."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "E-mail ou senha incorretos."
            "ERROR_USER_NOT_FOUND" -> "Não existe conta com esse e-mail."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Já existe uma conta com esse e-mail. Toque em Entrar."
            "ERROR_WEAK_PASSWORD" -> "Senha fraca: use pelo menos 6 caracteres."
            "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Espere um pouco e tente de novo."
            else -> e.message ?: "Erro de login."
        }
    }
    val m = e.message ?: return "Algo deu errado."
    return if (m.contains("PERMISSION_DENIED")) "Sem permissão para essa ação." else m
}
