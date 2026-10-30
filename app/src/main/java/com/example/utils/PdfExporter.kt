package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.MatingRecord
import com.example.data.PhotoRecord
import com.example.data.Rabbit
import com.example.data.TreatmentRecord
import com.example.data.WeightRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = 523f // 595 - 2*36
    private const val MAX_Y = 770f

    fun exportRabbitPdf(
        context: Context,
        rabbit: Rabbit,
        weights: List<WeightRecord>,
        treatments: List<TreatmentRecord>,
        photos: List<PhotoRecord> = emptyList(),
        matingRecords: List<MatingRecord> = emptyList(),
        allRabbits: List<Rabbit> = emptyList()
    ) {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Color & Paint definitions
        val colorPrimary = Color.parseColor("#1B3D2F")
        val colorSecondary = Color.parseColor("#2E7D32")
        val colorAccent = Color.parseColor("#C59B27")
        val colorDark = Color.parseColor("#212121")
        val colorGray = Color.parseColor("#616161")
        val colorLightGray = Color.parseColor("#EEEEEE")
        val colorBgCard = Color.parseColor("#F4F8F5")
        val colorRowAlt = Color.parseColor("#FAFCFA")

        val paintTitle = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintSub = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 10f
            isAntiAlias = true
        }
        val paintSectionHeader = Paint().apply {
            color = colorPrimary
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintLabel = Paint().apply {
            color = colorGray
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintValue = Paint().apply {
            color = colorDark
            textSize = 10f
            isAntiAlias = true
        }
        val paintValueBold = Paint().apply {
            color = colorDark
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTableHeader = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTableText = Paint().apply {
            color = colorDark
            textSize = 8.5f
            isAntiAlias = true
        }
        val paintFooter = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 8f
            isAntiAlias = true
        }
        val paintBorder = Paint().apply {
            color = Color.parseColor("#D0DCD4")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        // Load ABVFarm official logo
        val logoBitmap: Bitmap? = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.abvnewlogo)
        } catch (_: Exception) {
            null
        }

        fun drawFooter(c: Canvas, pNum: Int) {
            c.drawLine(MARGIN_X, 800f, MARGIN_X + CONTENT_WIDTH, 800f, paintBorder)
            c.drawText("Ferme ABV Sarl • Fiche Officielle d'Élevage & Carnet Zootechnique", MARGIN_X, 816f, paintFooter)
            val pageStr = "Page $pNum"
            val textWidth = paintFooter.measureText(pageStr)
            c.drawText(pageStr, MARGIN_X + CONTENT_WIDTH - textWidth, 816f, paintFooter)
        }

        fun drawRunningHeader(c: Canvas) {
            val pHeader = Paint().apply {
                color = colorPrimary
                style = Paint.Style.FILL
            }
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 40f, pHeader)

            var headTextX = MARGIN_X
            if (logoBitmap != null) {
                try {
                    val iconSize = 24f
                    val iconLeft = MARGIN_X
                    val iconTop = 8f
                    val path = Path().apply {
                        addRoundRect(RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), 5f, 5f, Path.Direction.CW)
                    }
                    c.save()
                    c.clipPath(path)
                    val scaledIcon = Bitmap.createScaledBitmap(logoBitmap, iconSize.toInt(), iconSize.toInt(), true)
                    c.drawBitmap(scaledIcon, iconLeft, iconTop, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    c.restore()
                    headTextX = MARGIN_X + iconSize + 8f
                } catch (_: Exception) {
                    headTextX = MARGIN_X
                }
            }

            val pHeadText = Paint().apply {
                color = Color.WHITE
                textSize = 9.5f
                isFakeBoldText = true
                isAntiAlias = true
            }
            c.drawText("ABV FARM • FICHE D'ÉLEVAGE : ${rabbit.name.uppercase()} (#${rabbit.id}) - Race ${rabbit.breed}", headTextX, 25f, pHeadText)
        }

        fun ensureSpace(neededHeight: Float): Float {
            // Note: will be updated inline
            return neededHeight
        }

        // --- PAGE 1: HEADER & BIOLOGY & PEDIGREE ---
        // Header Banner
        val pBanner = Paint().apply {
            color = colorPrimary
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, pBanner)

        // Accent gold line
        val pAccent = Paint().apply {
            color = colorAccent
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 107f, PAGE_WIDTH.toFloat(), 110f, pAccent)

        // Draw Official ABVFarm Logo prominently on the header banner
        var logoDrawn = false
        if (logoBitmap != null) {
            try {
                val logoSize = 74f
                val left = MARGIN_X
                val top = 18f
                val rect = RectF(left, top, left + logoSize, top + logoSize)

                // White backdrop card
                val pLogoBg = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, 14f, 14f, pLogoBg)

                // Clip and draw scaled logo inside
                val path = Path().apply {
                    addRoundRect(RectF(left + 2f, top + 2f, left + logoSize - 2f, top + logoSize - 2f), 12f, 12f, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(path)
                val scaled = Bitmap.createScaledBitmap(logoBitmap, (logoSize - 4).toInt(), (logoSize - 4).toInt(), true)
                canvas.drawBitmap(scaled, left + 2f, top + 2f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                canvas.restore()

                // Fine gold border accent around logo
                val pLogoBorder = Paint().apply {
                    color = colorAccent
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    isAntiAlias = true
                }
                canvas.drawRoundRect(rect, 14f, 14f, pLogoBorder)
                logoDrawn = true
            } catch (_: Exception) {
                logoDrawn = false
            }
        }

        val textStartX = if (logoDrawn) MARGIN_X + 86f else MARGIN_X

        val paintBrandTag = Paint().apply {
            color = colorAccent
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("ABV FARM  •  ÉLEVAGE CUNICOLE PROFESSIONNEL", textStartX, 32f, paintBrandTag)
        canvas.drawText(rabbit.name.uppercase(), textStartX, 52f, paintTitle)

        val genderText = if (rabbit.sex == "Male") "Reproducteur ♂ (Mâle)" else "Reproductrice ♀ (Femelle)"
        canvas.drawText("$genderText  •  Identifiant ID: #${rabbit.id}  •  Cage: ${rabbit.cageNumber.ifEmpty { "Non assignée" }}", textStartX, 70f, paintSub)
        canvas.drawText("Date d'édition : ${df.format(Date())}  •  Statut : ${if (rabbit.isActive) "Actif dans le cheptel" else rabbit.status}", textStartX, 86f, paintSub)

        var currentY = 135f

        // SECTION 1 : INFORMATIONS BIOLOGIQUES ET MORPHOLOGIE
        fun drawSectionTitle(title: String, icon: String = "") {
            val pSecBg = Paint().apply {
                color = colorBgCard
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(MARGIN_X, currentY - 14f, MARGIN_X + CONTENT_WIDTH, currentY + 10f, 6f, 6f, pSecBg)
            canvas.drawText("$icon $title", MARGIN_X + 10f, currentY + 3f, paintSectionHeader)
            currentY += 24f
        }

        drawSectionTitle("INFORMATIONS ZOOTECHNIQUES & IDENTITÉ", "📌")

        // 2-Column Info Card
        val cardTop = currentY
        val cardHeight = 110f
        val pCard = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(MARGIN_X, cardTop, MARGIN_X + CONTENT_WIDTH, cardTop + cardHeight, 10f, 10f, pCard)
        canvas.drawRoundRect(MARGIN_X, cardTop, MARGIN_X + CONTENT_WIDTH, cardTop + cardHeight, 10f, 10f, paintBorder)

        val ageMs = System.currentTimeMillis() - rabbit.dateOfBirth
        val ageDays = (ageMs / (24 * 60 * 60 * 1000L)).toInt()
        val ageMonths = ageDays / 30
        val ageRemainingDays = ageDays % 30
        val ageDisplay = if (ageMonths > 0) "$ageMonths mois et $ageRemainingDays j ($ageDays j)" else "$ageDays jours"

        val col1X = MARGIN_X + 16f
        val col2X = MARGIN_X + 270f
        var rowY = cardTop + 22f

        // Row 1
        canvas.drawText("Race / Variété :", col1X, rowY, paintLabel)
        canvas.drawText(rabbit.breed, col1X + 90f, rowY, paintValueBold)

        canvas.drawText("Poids Actuel :", col2X, rowY, paintLabel)
        canvas.drawText("${rabbit.weight} kg", col2X + 80f, rowY, paintValueBold)

        // Row 2
        rowY += 20f
        canvas.drawText("Date Naissance :", col1X, rowY, paintLabel)
        canvas.drawText(df.format(Date(rabbit.dateOfBirth)), col1X + 90f, rowY, paintValue)

        canvas.drawText("Âge Réel :", col2X, rowY, paintLabel)
        canvas.drawText(ageDisplay, col2X + 80f, rowY, paintValue)

        // Row 3
        rowY += 20f
        val sire = allRabbits.find { it.id == rabbit.fatherId }
        val dam = allRabbits.find { it.id == rabbit.motherId }

        canvas.drawText("Père (Sire) :", col1X, rowY, paintLabel)
        val sireText = sire?.let { "${it.name} (#${it.id})" } ?: if (rabbit.fatherId != null) "ID #${rabbit.fatherId}" else "Inconnu / Souche"
        canvas.drawText(sireText, col1X + 90f, rowY, paintValue)

        canvas.drawText("Mère (Dam) :", col2X, rowY, paintLabel)
        val damText = dam?.let { "${it.name} (#${it.id})" } ?: if (rabbit.motherId != null) "ID #${rabbit.motherId}" else "Inconnue / Souche"
        canvas.drawText(damText, col2X + 80f, rowY, paintValue)

        // Row 4
        rowY += 20f
        canvas.drawText("Notes / Origine :", col1X, rowY, paintLabel)
        val notesDisplay = rabbit.notes.ifEmpty { "Aucune observation particulière enregistrée." }
        val notesSnippet = if (notesDisplay.length > 55) notesDisplay.take(52) + "..." else notesDisplay
        canvas.drawText(notesSnippet, col1X + 90f, rowY, paintValue)

        currentY = cardTop + cardHeight + 20f

        // SECTION 2 : REPRODUCTION & PORTÉES
        val myMatings = matingRecords.filter { it.maleId == rabbit.id || it.femaleId == rabbit.id }.sortedByDescending { it.matingDate }
        val myOffspring = allRabbits.filter { it.fatherId == rabbit.id || it.motherId == rabbit.id }

        drawSectionTitle("HISTORIQUE DES REPRODUCTIONS & PORTÉES (${myMatings.size} saillies)", "🐇")

        if (myMatings.isEmpty()) {
            val pEmpty = Paint().apply {
                color = colorGray
                textSize = 9.5f
                isAntiAlias = true
            }
            canvas.drawText("Aucun accouplement répertorié pour ce reproducteur à ce jour.", MARGIN_X + 10f, currentY + 10f, pEmpty)
            currentY += 30f
        } else {
            // Table Header
            val tableTop = currentY
            val headerH = 18f
            val pTh = Paint().apply {
                color = colorPrimary
                style = Paint.Style.FILL
            }
            canvas.drawRect(MARGIN_X, tableTop, MARGIN_X + CONTENT_WIDTH, tableTop + headerH, pTh)

            val cDate = MARGIN_X + 8f
            val cPartner = MARGIN_X + 75f
            val cStatus = MARGIN_X + 195f
            val cExp = MARGIN_X + 310f
            val cKits = MARGIN_X + 395f
            val cNotes = MARGIN_X + 455f

            val thY = tableTop + 12f
            canvas.drawText("Date Saillie", cDate, thY, paintTableHeader)
            canvas.drawText("Partenaire", cPartner, thY, paintTableHeader)
            canvas.drawText("Statut", cStatus, thY, paintTableHeader)
            canvas.drawText("Mise bas", cExp, thY, paintTableHeader)
            canvas.drawText("Portée", cKits, thY, paintTableHeader)
            canvas.drawText("Observations", cNotes, thY, paintTableHeader)

            currentY = tableTop + headerH

            myMatings.take(8).forEachIndexed { idx, m ->
                val rowH = 18f
                val pRowBg = Paint().apply {
                    color = if (idx % 2 == 0) Color.WHITE else colorRowAlt
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowH, pRowBg)
                canvas.drawLine(MARGIN_X, currentY + rowH, MARGIN_X + CONTENT_WIDTH, currentY + rowH, paintBorder)

                val partnerId = if (m.maleId == rabbit.id) m.femaleId else m.maleId
                val partner = allRabbits.find { it.id == partnerId }
                val partnerName = partner?.let { "${it.name} (#${it.id})" } ?: "ID #$partnerId"

                val kits = myOffspring.filter {
                    it.dateOfBirth >= m.matingDate && (it.dateOfBirth - m.matingDate) <= 36L * 24 * 60 * 60 * 1000L
                }

                val rowTextY = currentY + 12f
                canvas.drawText(df.format(Date(m.matingDate)), cDate, rowTextY, paintTableText)
                canvas.drawText(if (partnerName.length > 18) partnerName.take(16) + ".." else partnerName, cPartner, rowTextY, paintTableText)
                canvas.drawText(m.status, cStatus, rowTextY, paintTableText)
                canvas.drawText(df.format(Date(m.expectedBirthDate)), cExp, rowTextY, paintTableText)
                canvas.drawText(if (m.status == "Mise bas") "${kits.size} lapereaux" else "-", cKits, rowTextY, paintTableText)
                val mNote = m.notes.ifEmpty { "-" }
                canvas.drawText(if (mNote.length > 12) mNote.take(10) + ".." else mNote, cNotes, rowTextY, paintTableText)

                currentY += rowH
            }
            currentY += 16f
        }

        // Check if we need to start Page 2 for Treatments and Weights
        val treatmentsNeededHeight = 40f + (treatments.size.coerceAtLeast(1) * 20f)
        val weightsNeededHeight = 40f + (weights.size.coerceAtLeast(1) * 20f)

        if (currentY + treatmentsNeededHeight > MAX_Y) {
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawRunningHeader(canvas)
            currentY = 60f
        }

        // SECTION 3 : CARNET SANITAIRE & VACCINATIONS
        drawSectionTitle("CARNET SANITAIRE & VACCINATIONS (${treatments.size} actes)", "💉")

        if (treatments.isEmpty()) {
            val pEmpty = Paint().apply {
                color = colorGray
                textSize = 9.5f
                isAntiAlias = true
            }
            canvas.drawText("Aucun traitement médical, vaccin ou vermifuge enregistré.", MARGIN_X + 10f, currentY + 10f, pEmpty)
            currentY += 30f
        } else {
            val tableTop = currentY
            val headerH = 18f
            val pTh = Paint().apply {
                color = colorSecondary
                style = Paint.Style.FILL
            }
            canvas.drawRect(MARGIN_X, tableTop, MARGIN_X + CONTENT_WIDTH, tableTop + headerH, pTh)

            val cDate = MARGIN_X + 8f
            val cType = MARGIN_X + 75f
            val cDesignation = MARGIN_X + 160f
            val cDosage = MARGIN_X + 290f
            val cRappel = MARGIN_X + 380f
            val cNotes = MARGIN_X + 440f

            val thY = tableTop + 12f
            canvas.drawText("Date", cDate, thY, paintTableHeader)
            canvas.drawText("Type Acte", cType, thY, paintTableHeader)
            canvas.drawText("Médicament / Produit", cDesignation, thY, paintTableHeader)
            canvas.drawText("Posologie", cDosage, thY, paintTableHeader)
            canvas.drawText("Rappel", cRappel, thY, paintTableHeader)
            canvas.drawText("Notes", cNotes, thY, paintTableHeader)

            currentY = tableTop + headerH

            treatments.forEachIndexed { idx, t ->
                // If page overflows, create a new page
                if (currentY + 22f > MAX_Y) {
                    drawFooter(canvas, pageNumber)
                    pdfDocument.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawRunningHeader(canvas)
                    currentY = 60f

                    // Re-draw table header on new page
                    canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + headerH, pTh)
                    canvas.drawText("Date", cDate, currentY + 12f, paintTableHeader)
                    canvas.drawText("Type Acte", cType, currentY + 12f, paintTableHeader)
                    canvas.drawText("Médicament / Produit", cDesignation, currentY + 12f, paintTableHeader)
                    canvas.drawText("Posologie", cDosage, currentY + 12f, paintTableHeader)
                    canvas.drawText("Rappel", cRappel, currentY + 12f, paintTableHeader)
                    canvas.drawText("Notes", cNotes, currentY + 12f, paintTableHeader)
                    currentY += headerH
                }

                val rowH = 18f
                val pRowBg = Paint().apply {
                    color = if (idx % 2 == 0) Color.WHITE else colorRowAlt
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowH, pRowBg)
                canvas.drawLine(MARGIN_X, currentY + rowH, MARGIN_X + CONTENT_WIDTH, currentY + rowH, paintBorder)

                val rowTextY = currentY + 12f
                canvas.drawText(df.format(Date(t.date)), cDate, rowTextY, paintTableText)
                canvas.drawText(t.type, cType, rowTextY, paintTableText)
                val des = if (t.designation.length > 20) t.designation.take(18) + ".." else t.designation
                canvas.drawText(des, cDesignation, rowTextY, paintTableText)
                val dos = if (t.dosage.length > 15) t.dosage.take(13) + ".." else t.dosage
                canvas.drawText(dos.ifEmpty { "-" }, cDosage, rowTextY, paintTableText)
                val rappelText = t.nextDueDate?.let { df.format(Date(it)) } ?: "-"
                canvas.drawText(rappelText, cRappel, rowTextY, paintTableText)
                val tNote = t.notes.ifEmpty { "-" }
                canvas.drawText(if (tNote.length > 14) tNote.take(12) + ".." else tNote, cNotes, rowTextY, paintTableText)

                currentY += rowH
            }
            currentY += 16f
        }

        // SECTION 4 : HISTORIQUE DES PESÉES & CROISSANCE
        if (currentY + weightsNeededHeight > MAX_Y) {
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawRunningHeader(canvas)
            currentY = 60f
        }

        drawSectionTitle("HISTORIQUE DES PESÉES DE CROISSANCE (${weights.size} pesées)", "⚖️")

        if (weights.isEmpty()) {
            val pEmpty = Paint().apply {
                color = colorGray
                textSize = 9.5f
                isAntiAlias = true
            }
            canvas.drawText("Aucune pesée périodique enregistrée pour ce lapin.", MARGIN_X + 10f, currentY + 10f, pEmpty)
            currentY += 30f
        } else {
            val tableTop = currentY
            val headerH = 18f
            val pTh = Paint().apply {
                color = colorPrimary
                style = Paint.Style.FILL
            }
            canvas.drawRect(MARGIN_X, tableTop, MARGIN_X + CONTENT_WIDTH, tableTop + headerH, pTh)

            val cDate = MARGIN_X + 16f
            val cWeight = MARGIN_X + 140f
            val cGain = MARGIN_X + 260f
            val cStatus = MARGIN_X + 390f

            val thY = tableTop + 12f
            canvas.drawText("Date de la pesée", cDate, thY, paintTableHeader)
            canvas.drawText("Poids (kg)", cWeight, thY, paintTableHeader)
            canvas.drawText("Variation / Gain", cGain, thY, paintTableHeader)
            canvas.drawText("Évolution / Tendance", cStatus, thY, paintTableHeader)

            currentY = tableTop + headerH

            weights.forEachIndexed { idx, w ->
                if (currentY + 22f > MAX_Y) {
                    drawFooter(canvas, pageNumber)
                    pdfDocument.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawRunningHeader(canvas)
                    currentY = 60f

                    canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + headerH, pTh)
                    canvas.drawText("Date de la pesée", cDate, currentY + 12f, paintTableHeader)
                    canvas.drawText("Poids (kg)", cWeight, currentY + 12f, paintTableHeader)
                    canvas.drawText("Variation / Gain", cGain, currentY + 12f, paintTableHeader)
                    canvas.drawText("Évolution / Tendance", cStatus, currentY + 12f, paintTableHeader)
                    currentY += headerH
                }

                val rowH = 18f
                val pRowBg = Paint().apply {
                    color = if (idx % 2 == 0) Color.WHITE else colorRowAlt
                    style = Paint.Style.FILL
                }
                canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowH, pRowBg)
                canvas.drawLine(MARGIN_X, currentY + rowH, MARGIN_X + CONTENT_WIDTH, currentY + rowH, paintBorder)

                val next = weights.getOrNull(idx + 1)
                val gainText = if (next != null) {
                    val g = w.weight - next.weight
                    String.format(Locale.getDefault(), "%+.2f kg", g)
                } else {
                    "Pesée initiale"
                }

                val evolution = if (next != null) {
                    val g = w.weight - next.weight
                    if (g > 0.0) "En croissance progressive" else if (g < 0.0) "Légère baisse de poids" else "Poids stable"
                } else {
                    "Point de départ"
                }

                val rowTextY = currentY + 12f
                canvas.drawText(df.format(Date(w.recordDate)), cDate, rowTextY, paintTableText)
                canvas.drawText("${w.weight} kg", cWeight, rowTextY, paintTableText)
                canvas.drawText(gainText, cGain, rowTextY, paintTableText)
                canvas.drawText(evolution, cStatus, rowTextY, paintTableText)

                currentY += rowH
            }
            currentY += 20f
        }

        // OFFICIAL CERTIFICATION STAMP
        if (currentY + 60f > MAX_Y) {
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawRunningHeader(canvas)
            currentY = 60f
        }

        val stampTop = currentY
        val pStampBox = Paint().apply {
            color = Color.parseColor("#F9FBF9")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(MARGIN_X, stampTop, MARGIN_X + CONTENT_WIDTH, stampTop + 54f, 8f, 8f, pStampBox)
        canvas.drawRoundRect(MARGIN_X, stampTop, MARGIN_X + CONTENT_WIDTH, stampTop + 54f, 8f, 8f, paintBorder)

        var stampTextX = MARGIN_X + 12f
        if (logoBitmap != null) {
            try {
                val stampLogoSize = 38f
                val stampLogoLeft = MARGIN_X + 10f
                val stampLogoTop = stampTop + 8f
                val path = Path().apply {
                    addRoundRect(RectF(stampLogoLeft, stampLogoTop, stampLogoLeft + stampLogoSize, stampLogoTop + stampLogoSize), 6f, 6f, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(path)
                val scaledStamp = Bitmap.createScaledBitmap(logoBitmap, stampLogoSize.toInt(), stampLogoSize.toInt(), true)
                canvas.drawBitmap(scaledStamp, stampLogoLeft, stampLogoTop, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                stampTextX = MARGIN_X + stampLogoSize + 20f
            } catch (_: Exception) {
                stampTextX = MARGIN_X + 12f
            }
        }

        val pStampText = Paint().apply {
            color = colorPrimary
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("DOCUMENT OFFICIEL CERTIFIÉ CONFORME PAR L'ÉLEVAGE ABV FARM", stampTextX, stampTop + 20f, pStampText)
        val pStampSub = Paint().apply {
            color = colorGray
            textSize = 8f
            isAntiAlias = true
        }
        canvas.drawText("Certifié conforme aux registres de la Ferme ABV Sarl le ${df.format(Date())}. Tout droit réservé.", stampTextX, stampTop + 36f, pStampSub)

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        // Write to cache and initiate sharing intent
        val cacheDir = context.cacheDir
        val safeName = rabbit.name.replace(Regex("[^a-zA-Z0-9]"), "_").trim()
        val pdfFile = File(cacheDir, "Fiche_Dossier_${safeName}_#${rabbit.id}.pdf")

        try {
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Partager la Fiche Complète du Lapin")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Toast.makeText(context, "Fiche PDF complète générée avec succès !", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erreur PDF : " + e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }
}
