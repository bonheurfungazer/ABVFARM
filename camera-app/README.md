# ABV Farm Caméra 📷🐰

Application Android de caméra **spécialement conçue pour ABV Farm**
(élevage cunicole — Douala, Cameroun). Slogan : *« Nourrir l'avenir, naturellement »*.

## Fonctions

- **Photo haute résolution** — résolution maximale du capteur
  (`CAPTURE_MODE_MAXIMIZE_QUALITY`, JPEG qualité 100), mise au point au toucher.
- **Vidéo haute résolution** — UHD si le téléphone la propose, sinon FHD puis HD,
  débit d'encodage élevé (24 Mbit/s) pour garder les détails.
- **Filigrane ABV Farm automatique** — le logo fourni par la ferme est incrusté :
  - en temps réel dans l'aperçu,
  - dans chaque image des **vidéos** (CameraX `OverlayEffect`, cibles
    Preview + VideoCapture + ImageCapture),
  - dans le fichier **JPEG** de chaque photo (incrustation après capture,
    garantie sur tous les téléphones, en plus de l'effet temps réel).
  Le filigrane = badge circulaire du logo en bas à droite + signature dorée
  « ABV Farm — Nourrir l'avenir, naturellement » en bas à gauche.
- **Réduction de bruit (bouton « Anti-bruit »)** — applique au capteur le mode
  Camera2 `NOISE_REDUCTION_MODE_HIGH_QUALITY` + `EDGE_MODE_HIGH_QUALITY`
  (aperçu, photo et vidéo) et active la stabilisation vidéo quand elle est
  disponible. Activée par défaut, désactivable en un toucher.
- **Enregistrement dans la galerie** — photos dans `Pictures/ABV Farm Camera`,
  vidéos dans `Movies/ABV Farm Camera`, bouton Galerie intégré.
- Caméra avant / arrière, minuteur d'enregistrement, aucun compte, aucun
  serveur : tout reste sur le téléphone.

## Technique

- Kotlin + CameraX 1.4.2 (`camera-core`, `camera-camera2`, `camera-view`,
  `camera-video`, `camera-effects`, `camera-extensions`).
- `applicationId` : `com.abvfarm.camera` — minSdk 24 (Android 7+), targetSdk 34.
- Le filigrane détouré (`watermark_badge.png`) est généré depuis le logo
  original fourni par Mr Robot (cercle du logo, fond transparent).

## Compiler

```bash
gradle :app:assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## Sur GitHub

Ce projet vit dans le dépôt **bonheurfungazer/ABVFARM**, dossier `camera-app/`.
Les 3 images (filigrane détouré, logo icône, logo original) y sont stockées en
base64 dans `tools/assets-base64/` : après clonage, lancer une fois
`tools/restore-assets.sh` pour les remettre dans
`app/src/main/res/drawable-nodpi/` avant de compiler.
