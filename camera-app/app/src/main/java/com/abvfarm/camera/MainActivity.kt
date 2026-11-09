package com.abvfarm.camera

import android.Manifest
import android.content.ActivityNotFoundException
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
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
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
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ABV Farm Caméra — caméra dédiée à l'élevage ABV Farm (v1.2.0).
 *
 * - Photo haute résolution (plafonnée ~12 Mpx pour rester sûre sur les
 *   téléphones à 3 Go de RAM) avec filigrane ABV Farm incrusté dans le JPEG
 *   enregistré (incrustation après capture, garantie sur tout appareil) et
 *   flash Non/Auto/Oui.
 * - Vidéo haute résolution (UHD si l'appareil le permet, sinon FHD/HD) avec
 *   filigrane incrusté via OverlayEffect (cible VIDEO_CAPTURE, combinaison
 *   autorisée par CameraX) et lampe torche.
 * - Réduction de bruit activable : NOISE_REDUCTION_MODE_HIGH_QUALITY +
 *   EDGE_MODE_HIGH_QUALITY + stabilisation vidéo quand elle est proposée.
 *
 * Historique des correctifs v1.2.0 (audit complet 24 agents) :
 * l'OverlayEffect ne doit JAMAIS recevoir une combinaison de cibles hors de
 * [PREVIEW, VIDEO_CAPTURE, PREVIEW|VIDEO_CAPTURE,
 * IMAGE_CAPTURE|PREVIEW|VIDEO_CAPTURE] — la combinaison photo de la v1.1
 * plantait l'app dès l'ouverture (IllegalArgumentException du constructeur).
 * Le mode photo n'utilise donc aucun effet ; la vidéo utilise VIDEO_CAPTURE
 * seul, construit dans un try/catch avec repli signalé à l'utilisateur.
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
    private lateinit var btnPermission: Button
    private lateinit var segPhoto: TextView
    private lateinit var segVideo: TextView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var overlayEffect: OverlayEffect? = null
    private var effectThread: HandlerThread? = null
    private var videoEffectActive = false

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var videoMode = false
    private var noiseReductionOn = true
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchOn = false
    private var isCapturing = false
    private var isFinalizing = false
    private var recStarted = false
    private var pendingStop = false
    private var recNoAudio = false
    private var interruptedRecording = false
    private var bindGeneration = 0

    /**
     * Palier de compatibilité vidéo (persisté) : 0 = tout activé (options
     * capteur + effet filigrane), 1 = sans options capteur avancées,
     * 2 = vidéo simple sans effet. Descendu automatiquement après chaque
     * erreur d'enregistrement jusqu'à trouver le réglage accepté par
     * l'appareil (certains téléphones d'entrée de gamme rejettent les
     * options avancées ou l'effet dans la chaîne d'encodage).
     */
    private var videoCompatLevel = 0
    private var lastMediaUri: Uri? = null
    private var lastMediaMime: String? = null
    private var crashDialog: AlertDialog? = null

    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordStartMs = 0L

    private val badgeBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.watermark_badge)
    }

    // Objets de dessin réutilisés par l'effet vidéo (aucune allocation par image)
    private val wmBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }
    private val wmDst = RectF()

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (recording != null && !isFinalizing) {
                val s = (System.currentTimeMillis() - recordStartMs) / 1000
                txtRecording.text = String.format(Locale.FRANCE, "● ENR %02d:%02d", s / 60, s % 60)
                mainHandler.postDelayed(this, 500)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasCameraPermission()) {
            btnPermission.visibility = View.GONE
            startCamera()
        } else {
            onCameraPermissionMissing()
        }
    }

    private val audioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* le résultat est relu au démarrage de l'enregistrement */ }

    // ------------------------------------------------------------ diagnostic
    // Le handler est installé une seule fois pour tout le processus, sans
    // capturer l'Activity ; il écrit la cause dans le stockage interne privé.

    private fun incidentFile(): File = File(filesDir, "abv_incident.txt")

    private fun recordIncident(detail: String) {
        try {
            incidentFile().writeText(
                "ABV Farm Caméra $VERSION_LABEL — souci vidéo\n$detail\n" +
                    "(étape : $startupStep, thread : ${Thread.currentThread().name})"
            )
        } catch (_: Throwable) {
        }
    }

    private fun showIncidentIfAny() {
        if (isFinishing || isDestroyed) return
        try {
            val f = incidentFile()
            if (!f.exists()) return
            val text = f.readText()
            AlertDialog.Builder(this)
                .setTitle("Détail du dernier souci vidéo")
                .setMessage(
                    "Faites une capture d'écran de ce message et envoyez-la pour correction.\n\n" + text
                )
                .setPositiveButton("J'ai envoyé la capture") { _, _ -> }
                .setNegativeButton("Fermer") { _, _ -> }
                .setOnDismissListener { try { f.delete() } catch (_: Throwable) {} }
                .show()
        } catch (_: Throwable) {
        }
    }

    private fun showPreviousCrashIfAny(): Boolean {
        if (isFinishing || isDestroyed) return false
        try {
            val f = crashFileFor(applicationContext)
            if (!f.exists()) return false
            val text = f.readText()
            val scroll = android.widget.ScrollView(this)
            val tv = TextView(this).apply {
                setText(
                    "L'application s'est arrêtée au démarrage précédent. " +
                        "Faites une capture d'écran de ce message et envoyez-la pour correction.\n\n" + text
                )
                setTextColor(Color.WHITE)
                setPadding(40, 40, 40, 40)
                textSize = 12f
            }
            scroll.addView(tv)
            crashDialog = AlertDialog.Builder(this)
                .setTitle("Erreur détectée au démarrage précédent")
                .setView(scroll)
                .setPositiveButton("J'ai envoyé la capture") { _, _ -> }
                .setNegativeButton("Fermer") { _, _ -> }
                .setOnDismissListener { try { f.delete() } catch (_: Throwable) {} }
                .show()
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    // ---------------------------------------------------------------- cycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startupStep = "onCreate"
        installCrashRecorder(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()
        try {
            setContentView(R.layout.activity_main)
        } catch (e: Throwable) {
            recordCrash(applicationContext, "affichage de l'écran (setContentView)", e)
            val tv = TextView(this).apply {
                setText("L'écran n'a pas pu s'afficher.\n\n" + e.stackTraceToString())
                setTextColor(Color.WHITE)
                setPadding(40, 80, 40, 40)
                textSize = 12f
            }
            try {
                setContentView(android.widget.ScrollView(this).apply { addView(tv) })
            } catch (_: Throwable) {
            }
            return
        }
        startupStep = "écran affiché"

        previewView = findViewById(R.id.previewView)
        txtStatus = findViewById(R.id.txtStatus)
        txtRecording = findViewById(R.id.txtRecording)
        txtQuality = findViewById(R.id.txtQuality)
        btnCapture = findViewById(R.id.btnCapture)
        captureInner = findViewById(R.id.captureInner)
        btnNoise = findViewById(R.id.btnNoise)
        lblNoise = findViewById(R.id.lblNoise)
        btnFlash = findViewById(R.id.btnFlash)
        btnPermission = findViewById(R.id.btnPermission)
        segPhoto = findViewById(R.id.btnModePhoto)
        segVideo = findViewById(R.id.btnModeVideo)

        // Restauration de l'état après recréation de l'activité
        savedInstanceState?.let {
            videoMode = it.getBoolean("videoMode", false)
            lensFacing = it.getInt("lensFacing", CameraSelector.LENS_FACING_BACK)
            flashMode = it.getInt("flashMode", ImageCapture.FLASH_MODE_OFF)
            noiseReductionOn = it.getBoolean("noiseReductionOn", true)
            torchOn = it.getBoolean("torchOn", false)
        }

        findViewById<ImageButton>(R.id.btnSwitchCamera).setOnClickListener {
            if (busy()) return@setOnClickListener
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            torchOn = false // autre appareil : la lampe repart éteinte
            startCamera()
        }
        val noiseClick = View.OnClickListener {
            if (busy()) return@OnClickListener
            noiseReductionOn = !noiseReductionOn
            updateNoiseButton()
            startCamera()
            toast(if (noiseReductionOn) "Anti-bruit activé (haute qualité)" else "Anti-bruit désactivé")
        }
        btnNoise.setOnClickListener(noiseClick)
        findViewById<View>(R.id.colNoise).setOnClickListener(noiseClick)
        btnFlash.setOnClickListener { onFlashPressed() }
        segPhoto.setOnClickListener { setMode(video = false) }
        segVideo.setOnClickListener { setMode(video = true) }
        btnCapture.setOnClickListener {
            if (videoMode) toggleRecording() else takePhoto()
        }
        val galleryClick = View.OnClickListener { openGallery() }
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener(galleryClick)
        findViewById<View>(R.id.colGallery).setOnClickListener(galleryClick)
        btnPermission.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionLauncher.launch(permissionArray())
            } else {
                // Refus définitif : seul l'écran système peut débloquer
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                    toast("Autorisations → Caméra → Autoriser, puis revenez ici.")
                } catch (_: Exception) {
                    permissionLauncher.launch(permissionArray())
                }
            }
        }

        setupTouchControls()

        // La barre de navigation ne doit pas recouvrir les commandes du bas
        val bottomBlock = findViewById<View>(R.id.bottomBlock)
        val baseBottomPadding = bottomBlock.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomBlock) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottomPadding + nav.bottom)
            insets
        }

        videoCompatLevel = getSharedPreferences("abv_camera", MODE_PRIVATE)
            .getInt("video_compat_level", 0)

        updateNoiseButton()
        updateModeUi()
        updateFlashChip()
        startupStep = "interface prête"
        if (!showPreviousCrashIfAny()) showIncidentIfAny()
        if (hasCameraPermission()) startCamera() else onCameraPermissionMissing()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("videoMode", videoMode)
        outState.putInt("lensFacing", lensFacing)
        outState.putInt("flashMode", flashMode)
        outState.putBoolean("noiseReductionOn", noiseReductionOn)
        outState.putBoolean("torchOn", torchOn)
    }

    override fun onResume() {
        super.onResume()
        if (interruptedRecording) {
            interruptedRecording = false
            toast("Enregistrement interrompu : l'application était passée en arrière-plan.")
        }
        if (hasCameraPermission() && camera == null && !isFinishing) {
            btnPermission.visibility = View.GONE
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (recording != null && !isFinalizing) {
            // CameraX arrête l'enregistrement quand l'app passe en arrière-plan :
            // on le finalise proprement et on prévient au retour.
            isFinalizing = true
            interruptedRecording = true
            try {
                recording?.stop()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(tickRunnable)
        try {
            crashDialog?.dismiss()
        } catch (_: Exception) {
        }
        try {
            recording?.close()
        } catch (_: Exception) {
        }
        recording = null
        closeEffect()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    // ------------------------------------------------------------ permissions

    private fun permissionArray(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun onCameraPermissionMissing() {
        btnPermission.visibility = View.VISIBLE
        txtStatus.text = "Caméra non autorisée — touchez « Autoriser la caméra »."
    }

    private fun busy(): Boolean = recording != null || isFinalizing || isCapturing

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
        lblNoise.text = if (noiseReductionOn) "Anti-bruit : Oui" else "Anti-bruit : Non"
        btnNoise.contentDescription = if (noiseReductionOn) "Anti-bruit activé" else "Anti-bruit désactivé"
    }

    /** Bouton flash : en photo, cycle Non → Auto → Oui ; en vidéo, lampe torche confirmée par CameraX. */
    private fun onFlashPressed() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        if (!hasFlash) {
            toast(if (videoMode) "Pas de lampe sur cette caméra." else "Pas de flash sur cette caméra.")
            return
        }
        if (videoMode) {
            val target = !torchOn
            val future = camera?.cameraControl?.enableTorch(target) ?: return
            future.addListener({
                try {
                    future.get()
                    torchOn = target
                    updateFlashChip()
                    toast(if (target) "Lampe allumée" else "Lampe éteinte")
                } catch (e: Exception) {
                    toast("La lampe n'a pas pu être ${if (target) "allumée" else "éteinte"}.")
                }
            }, ContextCompat.getMainExecutor(this))
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
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        btnFlash.isEnabled = hasFlash
        btnFlash.alpha = if (hasFlash) 1f else 0.45f
        val active: Boolean
        if (videoMode) {
            btnFlash.text = if (!hasFlash) "Lampe : —" else if (torchOn) "Lampe : Oui" else "Lampe : Non"
            active = hasFlash && torchOn
        } else {
            btnFlash.text = when {
                !hasFlash -> "Flash : —"
                flashMode == ImageCapture.FLASH_MODE_AUTO -> "Flash : Auto"
                flashMode == ImageCapture.FLASH_MODE_ON -> "Flash : Oui"
                else -> "Flash : Non"
            }
            active = hasFlash && flashMode != ImageCapture.FLASH_MODE_OFF
        }
        btnFlash.setBackgroundResource(
            if (active) R.drawable.bg_flash_chip_active else R.drawable.bg_flash_chip
        )
        btnFlash.setTextColor(if (active) 0xFF0B2E21.toInt() else Color.WHITE)
        btnFlash.compoundDrawableTintList =
            ColorStateList.valueOf(if (active) 0xFF0B2E21.toInt() else Color.WHITE)
    }

    private fun setMode(video: Boolean) {
        if (busy() || video == videoMode) return
        videoMode = video
        if (videoMode && !hasAudioPermission()) {
            // Le micro n'est demandé qu'au premier passage en vidéo
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
            txtQuality.text = "Vidéo — meilleure qualité de l'appareil"
        } else {
            segPhoto.setBackgroundResource(R.drawable.bg_mode_selected)
            segPhoto.setTextColor(0xFF0B2E21.toInt())
            segVideo.background = null
            segVideo.setTextColor(Color.WHITE)
            captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_white)
            txtStatus.text = "Mode photo — filigrane ABV Farm ajouté automatiquement"
            txtQuality.text = "Photo — meilleure qualité de l'appareil"
        }
        updateFlashChip()
    }

    // ------------------------------------------------------- toucher / zoom

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var multiTouchSeen = false

    private fun setupTouchControls() {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val info = cam.cameraInfo.zoomState.value ?: return false
                val target = (info.zoomRatio * detector.scaleFactor)
                    .coerceIn(info.minZoomRatio, info.maxZoomRatio)
                cam.cameraControl.setZoomRatio(target)
                return true
            }
        })
        previewView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    downTime = System.currentTimeMillis()
                    multiTouchSeen = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> multiTouchSeen = true
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val quick = System.currentTimeMillis() - downTime < 350
                    if (!multiTouchSeen && event.pointerCount == 1 && quick &&
                        dx * dx + dy * dy < 40f * 40f
                    ) {
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(
                            point,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                    }
                    v.performClick()
                }
            }
            true
        }
    }

    // ---------------------------------------------------------------- caméra

    private fun nrMode(): Int =
        if (noiseReductionOn) CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
        else CameraMetadata.NOISE_REDUCTION_MODE_OFF

    private fun edgeMode(): Int =
        if (noiseReductionOn) CameraMetadata.EDGE_MODE_HIGH_QUALITY
        else CameraMetadata.EDGE_MODE_OFF

    private fun closeEffect() {
        try {
            overlayEffect?.close()
        } catch (_: Exception) {
        }
        overlayEffect = null
        videoEffectActive = false
        try {
            effectThread?.quitSafely()
        } catch (_: Exception) {
        }
        effectThread = null
    }

    private fun startCamera() {
        if (!hasCameraPermission()) {
            onCameraPermissionMissing()
            return
        }
        startupStep = "démarrage caméra"
        val generation = ++bindGeneration
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (generation != bindGeneration || isFinishing || isDestroyed) return@addListener
            startupStep = "configuration caméra"
            val provider = try {
                providerFuture.get()
            } catch (e: Exception) {
                recordCrash(applicationContext, "initialisation du fournisseur caméra", e)
                txtStatus.text = "Caméra indisponible — fermez les autres applications qui l'utilisent, puis réessayez."
                toast("Caméra indisponible sur cet appareil.")
                return@addListener
            }

            // --- Aperçu ---
            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                .setCaptureRequestOption(CaptureRequest.EDGE_MODE, edgeMode())
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // --- Un seul usage de capture selon le mode (fiabilité) ---
            imageCapture = null
            videoCapture = null
            if (!videoMode) {
                // Plafond ~12 Mpx : la pleine résolution d'un capteur 48 Mpx
                // ferait exploser la mémoire à l'incrustation (OOM sur 3 Go).
                val photoSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(4032, 3024),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val imageBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(photoSelector)
                    .setJpegQuality(95)
                    .setFlashMode(flashMode)
                Camera2Interop.Extender(imageBuilder)
                    .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                    .setCaptureRequestOption(CaptureRequest.EDGE_MODE, edgeMode())
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
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                if (videoCompatLevel == 0) {
                    // Options capteur avancées : rejetées par la chaîne
                    // d'encodage de certains appareils → palier 1 s'en passe.
                    Camera2Interop.Extender(videoBuilder)
                        .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode())
                        .setCaptureRequestOption(CaptureRequest.EDGE_MODE, edgeMode())
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            if (noiseReductionOn) CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                            else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                        )
                }
                videoCapture = videoBuilder.build()
            }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            startupStep = "liaison caméra"

            fun bind(withEffect: Boolean): Boolean {
                // On détache TOUT avant de fermer l'ancien effet (il peut être
                // encore attaché au groupe précédent).
                provider.unbindAll()
                closeEffect()
                var effect: OverlayEffect? = null
                if (withEffect && videoMode) {
                    effect = try {
                        val thread = HandlerThread("abv-effect").apply { start() }
                        val eff = OverlayEffect(
                            CameraEffect.VIDEO_CAPTURE, 0, Handler(thread.looper)
                        ) { e -> Log.w(TAG, "Effet filigrane en erreur: $e") }
                        eff.setOnDrawListener { frame ->
                            drawVideoWatermark(
                                frame.overlayCanvas,
                                frame.rotationDegrees,
                                frame.isMirroring
                            )
                            true
                        }
                        effectThread = thread
                        eff
                    } catch (e: Throwable) {
                        Log.e(TAG, "Création de l'effet filigrane impossible", e)
                        null
                    }
                }
                val groupBuilder = UseCaseGroup.Builder().addUseCase(preview)
                imageCapture?.let { groupBuilder.addUseCase(it) }
                videoCapture?.let { groupBuilder.addUseCase(it) }
                if (effect != null) groupBuilder.addEffect(effect)
                return try {
                    camera = provider.bindToLifecycle(this, selector, groupBuilder.build())
                    overlayEffect = effect
                    videoEffectActive = effect != null
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Liaison caméra en erreur", e)
                    try {
                        effect?.close()
                    } catch (_: Exception) {
                    }
                    if (effect != null) {
                        try {
                            effectThread?.quitSafely()
                        } catch (_: Exception) {
                        }
                        effectThread = null
                    }
                    false
                }
            }

            var bound = bind(withEffect = videoCompatLevel <= 1)
            if (!bound) bound = bind(withEffect = false)
            if (!bound && lensFacing == CameraSelector.LENS_FACING_FRONT) {
                // Caméra frontale absente/indisponible : retour à la dorsale
                lensFacing = CameraSelector.LENS_FACING_BACK
                toast("Caméra avant indisponible — retour à la caméra arrière.")
                startCamera()
                return@addListener
            }
            if (!bound) {
                txtStatus.text = "Caméra indisponible — fermez les autres applications qui l'utilisent, puis réessayez."
                toast("Caméra indisponible sur cet appareil.")
                return@addListener
            }
            // Restaurer la lampe si elle était allumée (même caméra, même mode)
            if (videoMode && torchOn && camera?.cameraInfo?.hasFlashUnit() == true) {
                camera?.cameraControl?.enableTorch(true)
            }
            if (videoMode && !videoEffectActive) {
                txtStatus.text = "Mode vidéo — ⚠ filigrane indisponible sur cet appareil"
                toast("Attention : le filigrane vidéo n'a pas pu être activé.")
            }
            startupStep = "caméra prête"
            updateFlashChip()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Filigrane vidéo : l'effet dessine dans l'espace du tampon capteur
     * (paysage), que l'encodeur redresse ensuite selon rotationDegrees —
     * sans compensation, le filigrane sortait tourné de 90° dans les vidéos
     * portrait (constaté sur le Galaxy A21s). On dessine donc pré-tourné en
     * sens inverse, dans un repère logique aux dimensions échangées, et on
     * corrige l'effet miroir éventuel (caméra frontale).
     */
    private fun drawVideoWatermark(canvas: Canvas, rotationDegrees: Int, mirroring: Boolean) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val bw = canvas.width
        val bh = canvas.height
        if (bw <= 0 || bh <= 0) return
        val rot = ((rotationDegrees % 360) + 360) % 360
        if (rot == 0 && !mirroring) {
            drawWatermarkContent(canvas, bw, bh)
            return
        }
        canvas.save()
        if (rot != 0) {
            canvas.translate(bw / 2f, bh / 2f)
            canvas.rotate(-rot.toFloat())
            if (rot == 90 || rot == 270) {
                canvas.translate(-bh / 2f, -bw / 2f)
            } else {
                canvas.translate(-bw / 2f, -bh / 2f)
            }
        }
        val logicalW = if (rot == 90 || rot == 270) bh else bw
        val logicalH = if (rot == 90 || rot == 270) bw else bh
        if (mirroring) {
            canvas.translate(logicalW.toFloat(), 0f)
            canvas.scale(-1f, 1f)
        }
        drawWatermarkContent(canvas, logicalW, logicalH)
        canvas.restore()
    }

    /**
     * Dessin du filigrane dans un repère logique donné : le logo exact fourni
     * par Mr Robot (fond détouré transparent), proportions conservées,
     * ancré en bas à droite. Le logo contient déjà le nom et le slogan —
     * aucun texte n'est ajouté par-dessus.
     */
    private fun drawWatermarkContent(canvas: Canvas, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val margin = w * 0.035f
        val badgeW = w * 0.26f
        val badgeH = badgeW * badgeBitmap.height / badgeBitmap.width
        wmDst.set(w - margin - badgeW, h - margin - badgeH, w - margin, h - margin)
        canvas.drawBitmap(badgeBitmap, null, wmDst, wmBadgePaint)
    }

    // ------------------------------------------------------------------ photo

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.FRANCE).format(System.currentTimeMillis())

    private fun takePhoto() {
        if (isCapturing) return
        val capture = imageCapture
        if (capture == null) {
            txtStatus.text = "Caméra en démarrage… réessayez dans un instant."
            return
        }
        startupStep = "capture photo"
        isCapturing = true
        btnCapture.alpha = 0.55f
        val st = stamp()
        val tempFile = File(cacheDir, "abv_$st.jpg")
        val metadata = ImageCapture.Metadata().apply {
            // Selfie cadré dans l'aperçu miroir = fichier final en miroir aussi
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val output = ImageCapture.OutputFileOptions.Builder(tempFile)
            .setMetadata(metadata)
            .build()
        txtStatus.text = "Capture en cours…"
        capture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                var uri: Uri? = null
                var withWatermark = true
                try {
                    uri = burnWatermarkAndSave(tempFile, st)
                } catch (t: Throwable) {
                    // JAMAIS perdre la photo : repli = enregistrer l'original
                    Log.e(TAG, "Incrustation impossible, repli sur l'original", t)
                    withWatermark = false
                    uri = try {
                        saveOriginalToGallery(tempFile, st)
                    } catch (t2: Throwable) {
                        Log.e(TAG, "Repli d'enregistrement impossible", t2)
                        null
                    }
                }
                tempFile.delete()
                val savedUri = uri
                val wm = withWatermark
                runOnUiThread {
                    isCapturing = false
                    btnCapture.alpha = 1f
                    if (isDestroyed) return@runOnUiThread
                    if (savedUri != null) {
                        lastMediaUri = savedUri
                        lastMediaMime = "image/jpeg"
                        if (wm) {
                            txtStatus.text = "📷 Photo enregistrée avec filigrane"
                            toast("Photo enregistrée avec le filigrane ABV Farm ✅")
                        } else {
                            txtStatus.text = "📷 Photo enregistrée (sans filigrane — mémoire insuffisante)"
                            toast("Photo enregistrée, mais sans filigrane cette fois.")
                        }
                    } else {
                        txtStatus.text = "Échec de l'enregistrement — vérifiez l'espace libre, puis réessayez."
                        toast("Échec de l'enregistrement de la photo.")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture photo en erreur", exception)
                runOnUiThread {
                    isCapturing = false
                    btnCapture.alpha = 1f
                    if (isDestroyed) return@runOnUiThread
                    txtStatus.text = "Échec de la capture photo — réessayez."
                    toast("Erreur de capture photo.")
                }
            }
        })
    }

    /** Charge la photo temporaire, corrige l'orientation, dessine le filigrane, enregistre dans la galerie. */
    private fun burnWatermarkAndSave(tempFile: File, st: String): Uri {
        startupStep = "incrustation photo"
        var bitmap = BitmapFactory.decodeFile(
            tempFile.absolutePath,
            BitmapFactory.Options().apply { inMutable = true }
        ) ?: throw IllegalStateException("Photo illisible")
        // Orientation EXIF — les 8 cas
        try {
            val exif = ExifInterface(tempFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f); matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f); matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
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

        try {
            val canvas = Canvas(bitmap)
            val w = canvas.width
            val margin = w * 0.035f
            val badgeW = w * 0.26f
            val badgeH = badgeW * badgeBitmap.height / badgeBitmap.width
            canvas.drawBitmap(
                badgeBitmap, null,
                RectF(
                    w - margin - badgeW, canvas.height - margin - badgeH,
                    w - margin, canvas.height - margin
                ),
                wmBadgePaint
            )
            return writeJpegToGallery(bitmap, st)
        } finally {
            bitmap.recycle()
        }
    }

    /** Écrit un JPEG dans Pictures/ABV Farm Camera en vérifiant chaque étape. */
    private fun writeJpegToGallery(bitmap: Bitmap, st: String): Uri {
        val name = "ABV_Farm_$st.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ABV Farm Camera")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore a refusé la photo")
            try {
                val out = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Flux d'écriture indisponible")
                val ok = out.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                if (!ok) throw IllegalStateException("Compression JPEG échouée")
            } catch (e: Exception) {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                throw e
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ABV Farm Camera"
            )
            dir.mkdirs()
            val file = File(dir, name)
            val ok = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (!ok) {
                file.delete()
                throw IllegalStateException("Compression JPEG échouée")
            }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
    }

    /** Repli : copie le JPEG d'origine (sans filigrane) dans la galerie. */
    private fun saveOriginalToGallery(tempFile: File, st: String): Uri {
        val name = "ABV_Farm_$st.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ABV Farm Camera")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore a refusé la photo")
            try {
                val out = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Flux d'écriture indisponible")
                out.use { o -> FileInputStream(tempFile).use { it.copyTo(o) } }
            } catch (e: Exception) {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                throw e
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ABV Farm Camera"
            )
            dir.mkdirs()
            val file = File(dir, name)
            FileInputStream(tempFile).use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
    }

    // ------------------------------------------------------------------ vidéo

    private fun toggleRecording() {
        startupStep = "bascule enregistrement"
        val current = recording
        if (current != null) {
            if (isFinalizing) return
            isFinalizing = true
            txtStatus.text = "Finalisation de la vidéo…"
            if (recStarted) {
                try {
                    current.stop()
                } catch (_: Exception) {
                }
            } else {
                // Start pas encore arrivé : l'arrêt sera appliqué à son arrivée
                pendingStop = true
            }
            return
        }
        val capture = videoCapture
        if (capture == null) {
            txtStatus.text = "Caméra en démarrage… réessayez dans un instant."
            return
        }
        val st = stamp()
        val name = "ABV_Farm_$st.mp4"
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

        recNoAudio = !hasAudioPermission()
        recStarted = false
        pendingStop = false
        try {
            var pending = capture.output.prepareRecording(this, output)
            if (!recNoAudio) pending = pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recStarted = true
                        if (pendingStop) {
                            pendingStop = false
                            try {
                                recording?.stop()
                            } catch (_: Exception) {
                            }
                            return@start
                        }
                        recordStartMs = System.currentTimeMillis()
                        txtRecording.visibility = View.VISIBLE
                        mainHandler.removeCallbacks(tickRunnable)
                        mainHandler.post(tickRunnable)
                        captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_rec)
                        val extras = buildString {
                            if (!videoEffectActive) append(" — SANS filigrane")
                            if (recNoAudio) append(" — sans son (micro non autorisé)")
                            if (!noiseReductionOn) append(" — anti-bruit coupé")
                        }
                        txtStatus.text = "Enregistrement…$extras"
                    }
                    is VideoRecordEvent.Finalize -> {
                        val finished = recording
                        recording = null
                        isFinalizing = false
                        recStarted = false
                        pendingStop = false
                        mainHandler.removeCallbacks(tickRunnable)
                        txtRecording.visibility = View.GONE
                        captureInner.setBackgroundResource(R.drawable.bg_shutter_inner_red)
                        if (isDestroyed) return@start
                        if (event.hasError()) {
                            Log.e(TAG, "Erreur vidéo: ${event.error}")
                            // Ne pas laisser de MP4 cassé dans la galerie
                            try {
                                contentResolver.delete(event.outputResults.outputUri, null, null)
                            } catch (_: Exception) {
                            }
                            recordIncident(
                                "Erreur d'enregistrement vidéo, code ${event.error} " +
                                    "(palier de compatibilité $videoCompatLevel, " +
                                    "filigrane actif : $videoEffectActive, anti-bruit : $noiseReductionOn)."
                            )
                            // Paliers de compatibilité : on descend d'un cran et
                            // on relie la caméra, jusqu'au réglage accepté par
                            // l'appareil. Le palier est mémorisé entre lancements.
                            val nextLevel = when {
                                videoCompatLevel >= 2 -> -1
                                videoCompatLevel == 0 && !videoEffectActive -> 2
                                else -> videoCompatLevel + 1
                            }
                            if (nextLevel > videoCompatLevel) {
                                videoCompatLevel = nextLevel
                                getSharedPreferences("abv_camera", MODE_PRIVATE).edit()
                                    .putInt("video_compat_level", videoCompatLevel).apply()
                                txtStatus.text = "Réglage vidéo ajusté pour votre téléphone — réessayez l'enregistrement."
                                toast("Réglage vidéo ajusté (compatibilité) — réessayez.")
                                startCamera()
                            } else {
                                txtStatus.text = "Erreur d'enregistrement (code ${event.error}) — vérifiez l'espace libre, puis réessayez."
                                toast("Erreur d'enregistrement vidéo.")
                            }
                        } else {
                            lastMediaUri = event.outputResults.outputUri
                            lastMediaMime = "video/mp4"
                            txtStatus.text =
                                if (videoEffectActive) "🎥 Vidéo enregistrée avec filigrane"
                                else "🎥 Vidéo enregistrée (sans filigrane)"
                            toast("Vidéo enregistrée ✅")
                        }
                        finished?.let { /* déjà arrêté par l'événement */ }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Démarrage de l'enregistrement impossible", e)
            recording = null
            isFinalizing = false
            txtStatus.text = "L'enregistrement n'a pas pu démarrer — réessayez."
            toast("L'enregistrement n'a pas pu démarrer.")
        }
    }

    private fun openGallery() {
        try {
            val uri = lastMediaUri
            if (uri != null) {
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, lastMediaMime ?: "*/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            } else {
                startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"
                    )
                )
            }
        } catch (e: ActivityNotFoundException) {
            toast("Aucune application pour ouvrir la galerie.")
        } catch (e: Exception) {
            toast("Impossible d'ouvrir la galerie.")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "ABVFarmCamera"
        private const val VERSION_LABEL = "v1.2.2"

        @Volatile
        var startupStep: String = "avant onCreate"

        @Volatile
        private var crashRecorderInstalled = false

        fun crashFileFor(ctx: android.content.Context): File =
            File(ctx.filesDir, "abv_crash.txt")

        fun recordCrash(ctx: android.content.Context, where: String, error: Throwable) {
            try {
                crashFileFor(ctx).writeText(
                    "ABV Farm Caméra $VERSION_LABEL — plantage\n" +
                        "Où : $where (étape : $startupStep, thread : ${Thread.currentThread().name})\n\n" +
                        error.stackTraceToString()
                )
            } catch (_: Throwable) {
            }
        }

        @Synchronized
        fun installCrashRecorder(appCtx: android.content.Context) {
            if (crashRecorderInstalled) return
            crashRecorderInstalled = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    recordCrash(appCtx, "exception non rattrapée", error)
                } finally {
                    previous?.uncaughtException(thread, error)
                }
            }
        }
    }
}
