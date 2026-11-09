package com.abvfarm.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
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
 * ABV Farm Caméra — caméra dédiée à l'élevage ABV Farm.
 *
 * - Photo haute résolution (résolution maximale du capteur) avec filigrane
 *   ABV Farm incrusté dans le fichier JPEG enregistré.
 * - Vidéo haute résolution (UHD si disponible, sinon FHD/HD) avec le même
 *   filigrane incrusté dans chaque image de la vidéo via OverlayEffect.
 * - Réduction de bruit activable : mode « haute qualité » du capteur
 *   (Camera2 NOISE_REDUCTION_MODE_HIGH_QUALITY + EDGE_MODE_HIGH_QUALITY)
 *   appliqué à l'aperçu, à la photo et à la vidéo, plus stabilisation vidéo
 *   quand le téléphone la propose.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var txtStatus: TextView
    private lateinit var txtRecording: TextView
    private lateinit var txtQuality: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnNoise: Button
    private lateinit var btnModePhoto: Button
    private lateinit var btnModeVideo: Button

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var overlayEffect: OverlayEffect? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var videoMode = false
    private var noiseReductionOn = true
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
        btnNoise = findViewById(R.id.btnNoise)
        btnModePhoto = findViewById(R.id.btnModePhoto)
        btnModeVideo = findViewById(R.id.btnModeVideo)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        btnNoise.setOnClickListener {
            noiseReductionOn = !noiseReductionOn
            updateNoiseButton()
            startCamera()
            toast(if (noiseReductionOn) "Réduction de bruit activée (haute qualité)" else "Réduction de bruit désactivée")
        }
        btnModePhoto.setOnClickListener { setMode(video = false) }
        btnModeVideo.setOnClickListener { setMode(video = true) }
        btnCapture.setOnClickListener {
            if (videoMode) toggleRecording() else takePhoto()
        }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { openGallery() }

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

    private fun updateNoiseButton() {
        btnNoise.text = if (noiseReductionOn) "Anti-bruit : OUI" else "Anti-bruit : NON"
        btnNoise.setBackgroundColor(if (noiseReductionOn) 0xFF2E7D32.toInt() else 0xFF616161.toInt())
    }

    private fun setMode(video: Boolean) {
        if (recording != null) return
        videoMode = video
        btnModePhoto.setBackgroundColor(if (!video) 0xFFF5D98B.toInt() else 0x33FFFFFF)
        btnModePhoto.setTextColor(if (!video) 0xFF0B2E21.toInt() else Color.WHITE)
        btnModeVideo.setBackgroundColor(if (video) 0xFFF5D98B.toInt() else 0x33FFFFFF)
        btnModeVideo.setTextColor(if (video) 0xFF0B2E21.toInt() else Color.WHITE)
        btnCapture.text = if (video) "●" else "📸"
        txtStatus.text = if (video)
            "Mode vidéo — filigrane + anti-bruit incrustés dans l'enregistrement"
        else
            "Mode photo — filigrane ABV Farm ajouté automatiquement"
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

            // --- Photo : résolution maximale du capteur, qualité maximale ---
            val photoSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
            val imageBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(photoSelector)
                .setJpegQuality(100)
            Camera2Interop.Extender(imageBuilder)
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                .setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    if (noiseReductionOn) CameraMetadata.EDGE_MODE_HIGH_QUALITY
                    else CameraMetadata.EDGE_MODE_OFF
                )
            imageCapture = imageBuilder.build()

            // --- Vidéo : UHD > FHD > HD, gros débit pour la netteté ---
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(24_000_000)
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

            // --- Filigrane incrusté (aperçu + photo + vidéo) ---
            overlayEffect?.close()
            overlayEffect = OverlayEffect(
                CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE or CameraEffect.IMAGE_CAPTURE,
                0,
                Handler(Looper.getMainLooper())
            ) { e -> Log.w(TAG, "Effet filigrane en erreur: $e") }
            overlayEffect?.setOnDrawListener { frame ->
                drawWatermark(frame.overlayCanvas)
                true
            }

            val groupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .addUseCase(videoCapture!!)
            overlayEffect?.let { groupBuilder.addEffect(it) }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, groupBuilder.build())
                txtQuality.text = "Photo max • Vidéo UHD/FHD • Anti-bruit " +
                    if (noiseReductionOn) "HQ" else "off"
            } catch (e: Exception) {
                // Repli sans l'effet (certains téléphones anciens) : la photo garde
                // son filigrane grâce à l'incrustation après capture ci-dessous.
                Log.e(TAG, "Liaison avec effet impossible, repli sans effet", e)
                try {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        this, selector, preview, imageCapture!!, videoCapture!!
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Impossible de démarrer la caméra", e2)
                    toast("Caméra indisponible sur cet appareil.")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Dessine le filigrane ABV Farm sur une image (canvas d'effet vidéo/photo
     * ou bitmap photo après capture) : badge circulaire en bas à droite +
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
                        txtStatus.text = "📷 Photo enregistrée avec filigrane — Galerie / ABV Farm Caméra"
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
                    btnCapture.text = "⏹"
                    txtStatus.text = "Enregistrement… filigrane + anti-bruit actifs"
                }
                is VideoRecordEvent.Finalize -> {
                    mainHandler.removeCallbacks(tickRunnable)
                    txtRecording.visibility = View.GONE
                    btnCapture.text = "●"
                    if (event.hasError()) {
                        Log.e(TAG, "Erreur vidéo: ${event.error}")
                        txtStatus.text = "Erreur d'enregistrement vidéo"
                        toast("Erreur d'enregistrement vidéo.")
                    } else {
                        lastMediaUri = event.outputResults.outputUri
                        txtStatus.text = "🎥 Vidéo enregistrée avec filigrane — Galerie / ABV Farm Caméra"
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
