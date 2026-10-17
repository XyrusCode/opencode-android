package com.xyrus.opencode.ui

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera QR scanner for the pairing code.
 *
 * Uses the bundled ML Kit barcode model, so there is no Play Services
 * dependency and no model download: the decoder ships in the APK. Analysis runs
 * on a single background thread and the first QR wins, after which the camera
 * is torn down so a held-open camera cannot keep draining battery.
 */
@Composable
fun QrScannerSheet(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    // Guards against reporting the same code on consecutive frames.
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            handled.set(true)
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val analysis = ImageAnalysis.Builder()
                        // Dropping stale frames matters more than every frame:
                        // a pairing code is static on screen.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { proxy ->
                        analyze(proxy, scanner, handled, onScanned)
                    }

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure { Log.w(TAG, "camera bind failed", it) }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Bg.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Point at the QR from opencode pair",
                color = TextCol,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "The code works once and expires in 5 minutes.",
                color = TextDim,
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel scanning", tint = TextCol)
        }
    }
}

/** Hand one frame to ML Kit and report the first QR payload it recognises. */
private fun analyze(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    handled: AtomicBoolean,
    onScanned: (String) -> Unit,
) {
    if (handled.get()) {
        proxy.close()
        return
    }
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { codes ->
            val value = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
            if (value != null && handled.compareAndSet(false, true)) {
                onScanned(value)
            }
        }
        .addOnCompleteListener { proxy.close() }
}

private const val TAG = "QrScanner"