package com.abvfarm.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ABV Farm Caméra — caméra dédiée à l'élevage ABV Farm (v1.1).
 *
 * - Photo haute résolution (résolution maximale du capteur) avec filigrane
 *   ABV Farm incrusté dans le fichier JPEG enregistré + flash (Non/Auto/Oui).
 * - Vidéo haute résolution (UHD si l'appareil le permet, sinon FHD/HD) avec
 *   le même filigrane incrusté dans chaque image via OverlayEffect, et lampe
 *   torche pour filmer dans les bâtiments sombres.
 * - Réduction de bruit activable : mode « haute qualité » du capteur
 *   (NOISE_REDUCTION_MODE_HIGH_QUALITY + EDGE_MODE_HIGH_QUALITY) et
 *   stabilisation vidéo quand le téléphone la propose.
 *
 * Fiabilité (correctif v1.1) : un seul usage de capture est lié à la fois
 * (photo OU vidéo selon le mode) au lieu des trois simultanément, et le débit
 * vidéo n'est plus forcé — les téléphones d'entrée de gamme (ex. Galaxy A21s,
 * vidéo 1080p max) négocient ainsi proprement leur meilleure qualité au lieu
 * d'échouer à l'enregistrement.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var txtStatus: TextView
    private lateinit var txtRecording: TextView
    private lateinit var txtQuality: TextView
    private lateinit var btnCapture: View
    private lateinit var captureInner: View
    private lateinit var btnNoise: ImageButton
    private lateinit var lblNoise: TextView
    private lateinit var btnFlash: Button
    private lateinit var segPhoto: TextView
    private lateinit var segVideo: TextView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var overlayEffect: OverlayEffect? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var videoMode = false
    private var noiseReductionOn = true
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchOn = false
    private var lastMediaUri: Uri? = null

    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordStartMs = 0L

    private val badgeBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.watermark_badge)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (recording != null) {
                val s = (System.currentTimeMillis() - recordStartMs) / 1000
                txtRecording.text = String.format(Locale.FRANCE, "● REC %02d:%02d", s / 60, s % 60)
                mainHandler.postDelayed(this, 500)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            toast("Permission caméra refusée — l'application ne peut pas fonctionner.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        txtStatus = findViewById(R.id.txtStatus)
        txtRecording = findViewById(R.id.txtRecording)
        txtQuality = findViewById(R.id.txtQuality)
        btnCapture = findViewById(R.id.btnCapture)
        captureInner = findViewById(R.id.captureInner)
        btnNoise = findViewById(R.id.btnNoise)
        lblNoise = findViewById(R.id.lblNoise)
        btnFlash = findViewById(R.id.btnFlash)
        segPhoto = findViewById(R.id.btnModePhoto)
        segVideo = findViewById(R.id.btnModeVideo)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.btnSwitchCamera).setOnClickListener {
            if (recording != null) return@setOnClickListener
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        btnNoise.setOnClickListener {
            if (recording != null) return@setOnClickListener
            noiseReductionOn = !noiseReductionOn
            updateNoiseButton()
            startCamera()
            toast(if (noiseReductionOn) "Réduction de bruit activée (haute qualité)" else "Réduction de bruit désactivée")
        }
        btnFlash.setOnClickListener { onFlashPressed() }
        segPhoto.setOnClickListener { setMode(video = false) }
        segVideo.setOnClickListener { setMode(video = true) }
        btnCapture.setOnClickListener {
            if (videoMode) toggleRecording() else takePhoto()
        }
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener { openGallery() }

        // Mise au point au toucher
        previewView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                v.performClick()
            }
            true
        }

        updateNoiseButton()
        updateModeUi()
        updateFlashChip()
        if (hasPermissions()) startCamera()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.close()
        overlayEffect?.close()
        cameraExecutor.shutdown()
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------- interface

    private fun updateNoiseButton() {
        if (noiseReductionOn) {
            btnNoise.setBackgroundResource(R.drawable.bg_gold_circle)
            btnNoise.imageTintList = ColorStateList.valueOf(0xFF0B2E21.toInt())
            lblNoise.setTextColor(0xFFF5D98B.toInt())
        } else {
            btnNoise.setBackgroundResource(R.drawable.bg_glass_circle)
            btnNoise.imageTintList = ColorStateList.valueOf(Color.WHITE)
            lblNoise.setTextColor(Color.WHITE)
        }
    }

    /** Bouton flash : en photo, cycle Non → Auto → Oui ; en vidéo, lampe torche. */
    private fun onFlashPressed() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        if (!hasFlash) {
            toast("Pas de flash sur cette caméra.")
            return
        }
        if (videoMode) {
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            updateFlashChip()
            toast(if (torchOn) "Lampe allumée" else "Lampe éteinte")
        } else {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
            updateFlashChip()
        }
    }

    private fun updateFlashChip() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() != false
        btnFlash.alpha = if (hasFlash) 1f else 0.45f
        if (videoMode) {
            btnFlash.text = if (torchOn) "Lampe : Oui" else "Lampe : Non"
            val active = torchOn
            btnFlash.setBackgroundResource(
                if (active) R.drawable.bg_flash_chip_active else R.drawable.bg_flash_chip
            )
            btnFlash.setTextColor(if (active) 0xFF0B2E21.toInt() else Color.WHITE)
        } else {
            btnFlash.text = when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> "Flash : Auto"
                ImageCapture.FLASH_MODE_ON -> "Flash : Oui"
                else -> "Flash : Non"
            }
            val active = flashMode != ImageCapture.FLASH_MODE_OFF
            btnFlash.setBackgroundResource(
                if (active) R.drawable.bg_flash_chip_active else R.drawable.bg_flash_chip
            )
            btnFlash.setTextColor(if (active) 0xFF0B2E21.toInt() else Color.WHITE)
        }
    }

    private fun setMode(video: Boolean) {
        if (recording != null || video == videoMode) return
        videoMode = video
        updateModeUi()
        startCamera()
    }

    private fun updateModeUi() {
        if (videoMode) {
            segVideo.setBackgroundResource(R.drawable.bg_mode_selected)
            segVideo.setTextColor(0xFF0B2E21.toInt())
            segPhoto.background = null
            segPhoto.setTextColor(Color.WHITE)
            captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_red)
            txtStatus.text = "Mode vidéo — filigrane incrusté dans l'enregistrement"
            txtQuality.text = "Vidéo haute résolution (UHD / FHD selon l'appareil)"
        } else {
            segPhoto.setBackgroundResource(R.drawable.bg_mode_selected)
            segPhoto.setTextColor(0xFF0B2E21.toInt())
            segVideo.background = null
            segVideo.setTextColor(Color.WHITE)
            captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_white)
            txtStatus.text = "Mode photo — filigrane ABV Farm ajouté automatiquement"
            txtQuality.text = "Photo résolution maximale du capteur"
        }
        updateFlashChip()
    }

    // ---------------------------------------------------------------- caméra

    private fun nrMode(): Int =
        if (noiseReductionOn) CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
        else CameraMetadata.NOISE_REDUCTION_MODE_OFF

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            // --- Aperçu ---
            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                .setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    if (noiseReductionOn) CameraMetadata.EDGE_MODE_HIGH_QUALITY
                    else CameraMetadata.EDGE_MODE_OFF
                )
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // --- Un seul usage de capture selon le mode (fiabilité) ---
            imageCapture = null
            videoCapture = null
            if (!videoMode) {
                val photoSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
                val imageBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(photoSelector)
                    .setJpegQuality(100)
                    .setFlashMode(flashMode)
                Camera2Interop.Extender(imageBuilder)
                    .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                    .setCaptureRequestOption(
                        CaptureRequest.EDGE_MODE,
                        if (noiseReductionOn) CameraMetadata.EDGE_MODE_HIGH_QUALITY
                        else CameraMetadata.EDGE_MODE_OFF
                    )
                imageCapture = imageBuilder.build()
            } else {
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoBuilder = VideoCapture.Builder(recorder)
                Camera2Interop.Extender(videoBuilder)
                    .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                    .setCaptureRequestOption(
                        CaptureRequest.EDGE_MODE,
                        if (noiseReductionOn) CameraMetadata.EDGE_MODE_HIGH_QUALITY
                        else CameraMetadata.EDGE_MODE_OFF
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        if (noiseReductionOn) CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                videoCapture = videoBuilder.build()
            }

            // --- Filigrane incrusté dans les médias enregistrés ---
            // v1.1.1 : l'effet ne touche PLUS l'aperçu en direct (sur certains
            // téléphones d'entrée de gamme, l'effet sur l'aperçu pouvait le
            // figer en écran noir). L'aperçu reste donc toujours fluide, et le
            // filigrane est incrusté là où il compte : la photo et la vidéo
            // enregistrées (pour la photo, l'incrustation après capture le
            // garantit en plus sur tous les appareils).
            overlayEffect?.close()
            val targets = if (videoMode)
                CameraEffect.VIDEO_CAPTURE
            else
                CameraEffect.IMAGE_CAPTURE
            overlayEffect = OverlayEffect(
                targets, 0, Handler(Looper.getMainLooper())
            ) { e -> Log.w(TAG, "Effet filigrane en erreur: $e") }
            overlayEffect?.setOnDrawListener { frame ->
                drawWatermark(frame.overlayCanvas)
                true
            }

            val groupBuilder = UseCaseGroup.Builder().addUseCase(preview)
            imageCapture?.let { groupBuilder.addUseCase(it) }
            videoCapture?.let { groupBuilder.addUseCase(it) }
            overlayEffect?.let { groupBuilder.addEffect(it) }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            torchOn = false
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, groupBuilder.build())
            } catch (e: Exception) {
                // Repli sans l'effet (certains téléphones anciens) : la photo garde
                // son filigrane grâce à l'incrustation après capture ci-dessous.
                Log.e(TAG, "Liaison avec effet impossible, repli sans effet", e)
                try {
                    provider.unbindAll()
                    val plain = UseCaseGroup.Builder().addUseCase(preview)
                    imageCapture?.let { plain.addUseCase(it) }
                    videoCapture?.let { plain.addUseCase(it) }
                    camera = provider.bindToLifecycle(this, selector, plain.build())
                } catch (e2: Exception) {
                    Log.e(TAG, "Impossible de démarrer la caméra", e2)
                    toast("Caméra indisponible sur cet appareil.")
                }
            }
            updateFlashChip()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Dessine le filigrane ABV Farm : badge circulaire en bas à droite +
     * signature dorée en bas à gauche.
     */
    private fun drawWatermark(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val w = canvas.width
        val h = canvas.height
        if (w <= 0 || h <= 0) return

        val margin = w * 0.035f
        val size = w * 0.21f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 235
        }
        val dst = RectF(w - margin - size, h - margin - size, w - margin, h - margin)
        canvas.drawBitmap(badgeBitmap, null, dst, badgePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5D98B.toInt()
            textSize = (w * 0.028f).coerceAtLeast(14f)
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        canvas.drawText(
            "ABV Farm — Nourrir l'avenir, naturellement",
            margin,
            h - margin - size * 0.32f,
            textPaint
        )
    }

    // ------------------------------------------------------------------ photo

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(System.currentTimeMillis())
        val tempFile = File(cacheDir, "abv_$stamp.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        txtStatus.text = "Capture en cours…"
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                // Incrustation garantie du filigrane dans le JPEG final,
                // quel que soit le téléphone (en plus de l'effet temps réel).
                val uri = try {
                    burnWatermarkAndSave(tempFile, stamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Incrustation photo impossible", e)
                    null
                }
                tempFile.delete()
                runOnUiThread {
                    if (uri != null) {
                        lastMediaUri = uri
                        txtStatus.text = "📷 Photo enregistrée avec filigrane — Galerie / ABV Farm Camera"
                        toast("Photo enregistrée avec le filigrane ABV Farm ✅")
                    } else {
                        txtStatus.text = "Échec de l'enregistrement de la photo"
                        toast("Échec de l'enregistrement de la photo.")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture photo en erreur", exception)
                runOnUiThread { toast("Erreur de capture photo.") }
            }
        })
    }

    /** Charge la photo temporaire, corrige l'orientation, dessine le filigrane, enregistre dans la galerie. */
    private fun burnWatermarkAndSave(tempFile: File, stamp: String): Uri {
        var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            ?: throw IllegalStateException("Photo illisible")
        // Orientation EXIF
        try {
            val exif = ExifInterface(tempFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lecture EXIF impossible: $e")
        }

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (mutable != bitmap) bitmap.recycle()
        val canvas = Canvas(mutable)
        // Même dessin que l'effet, mais sur un canvas plein (sans l'effacement initial)
        val w = canvas.width
        val margin = w * 0.035f
        val size = w * 0.21f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }
        canvas.drawBitmap(
            badgeBitmap, null,
            RectF(w - margin - size, canvas.height - margin - size, w - margin, canvas.height - margin),
            badgePaint
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5D98B.toInt()
            textSize = (w * 0.028f).coerceAtLeast(14f)
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        canvas.drawText(
            "ABV Farm — Nourrir l'avenir, naturellement",
            margin, canvas.height - margin - size * 0.32f, textPaint
        )

        val name = "ABV_Farm_$stamp.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ABV Farm Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore a refusé la photo")
            contentResolver.openOutputStream(uri)?.use { out ->
                mutable.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            mutable.recycle()
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ABV Farm Camera"
            )
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { out -> mutable.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            mutable.recycle()
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            Uri.fromFile(file)
        }
    }

    // ------------------------------------------------------------------ vidéo

    private fun toggleRecording() {
        val current = recording
        if (current != null) {
            current.stop()
            recording = null
            return
        }
        val capture = videoCapture ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(System.currentTimeMillis())
        val name = "ABV_Farm_$stamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ABV Farm Camera")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordStartMs = System.currentTimeMillis()
                    txtRecording.visibility = View.VISIBLE
                    mainHandler.post(tickRunnable)
                    captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_rec)
                    txtStatus.text = "Enregistrement… filigrane + anti-bruit actifs"
                }
                is VideoRecordEvent.Finalize -> {
                    mainHandler.removeCallbacks(tickRunnable)
                    txtRecording.visibility = View.GONE
                    captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_red)
                    if (event.hasError()) {
                        Log.e(TAG, "Erreur vidéo: ${event.error}")
                        txtStatus.text = "Erreur d'enregistrement vidéo"
                        toast("Erreur d'enregistrement vidéo.")
                    } else {
                        lastMediaUri = event.outputResults.outputUri
                        txtStatus.text = "🎥 Vidéo enregistrée avec filigrane — Galerie / ABV Farm Camera"
                        toast("Vidéo enregistrée avec le filigrane ABV Farm ✅")
                    }
                }
            }
        }
    }

    private fun openGallery() {
        val uri = lastMediaUri
        if (uri != null) {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } else {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"
            ))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "ABVFarmCamera"
    }
}
