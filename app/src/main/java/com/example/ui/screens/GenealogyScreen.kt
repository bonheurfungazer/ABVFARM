package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.Rabbit
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.viewmodels.FarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenealogyScreen(navController: NavController, viewModel: FarmViewModel, rabbitId: Int) {
    val rabbits by viewModel.rabbits.collectAsState()
    val subject = rabbits.find { it.id == rabbitId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Arbre Généalogique (4G)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        if (subject == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Lapin introuvable.")
            }
            return@Scaffold
        }

        // ---- 4-GENERATION HIERARCHY ENGINE ----
        // G1: Sujet
        // subject is already defined.

        // G2: Parents
        val father = subject.fatherId?.let { id -> rabbits.find { it.id == id } }
        val mother = subject.motherId?.let { id -> rabbits.find { it.id == id } }

        // G3: Grands-Parents
        val fatherFather = father?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val fatherMother = father?.motherId?.let { id -> rabbits.find { it.id == id } }
        val motherFather = mother?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val motherMother = mother?.motherId?.let { id -> rabbits.find { it.id == id } }

        // G4: Arrière-Grands-Parents
        val fatherFatherFather = fatherFather?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val fatherFatherMother = fatherFather?.motherId?.let { id -> rabbits.find { it.id == id } }
        val fatherMotherFather = fatherMother?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val fatherMotherMother = fatherMother?.motherId?.let { id -> rabbits.find { it.id == id } }
        
        val motherFatherFather = motherFather?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val motherFatherMother = motherFather?.motherId?.let { id -> rabbits.find { it.id == id } }
        val motherMotherFather = motherMother?.fatherId?.let { id -> rabbits.find { it.id == id } }
        val motherMotherMother = motherMother?.motherId?.let { id -> rabbits.find { it.id == id } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Lignée de ${subject.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Glissez vers la droite ➡️ pour parcourir les 4 générations d'ancêtres : G1 (Sujet) • G2 (Parents) • G3 (Grands-parents) • G4 (Arrière-grands-parents).",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Diagram layout representation using horizontal scroll row with vertical centers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val treeHeight = 680.dp

                // ---- G1: SUJET ----
                Column(
                    modifier = Modifier.height(treeHeight).width(180.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GenealogyNodeCard(
                        label = "G1 : SUJET",
                        rabbit = subject,
                        defaultSex = subject.sex,
                        navController = navController
                    )
                }

                // ---- G2: PARENTS ----
                Column(
                    modifier = Modifier.height(treeHeight).width(180.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GenealogyNodeCard(
                        label = "G2 : PÈRE",
                        rabbit = father,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G2 : MÈRE",
                        rabbit = mother,
                        defaultSex = "Female",
                        navController = navController
                    )
                }

                // ---- G3: GRANDS-PARENTS ----
                Column(
                    modifier = Modifier.height(treeHeight).width(190.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GenealogyNodeCard(
                        label = "G3 : GP P. (Père de ${father?.name ?: "Père"})",
                        rabbit = fatherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G3 : GM P. (Mère de ${father?.name ?: "Père"})",
                        rabbit = fatherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G3 : GP M. (Père de ${mother?.name ?: "Mère"})",
                        rabbit = motherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G3 : GM M. (Mère de ${mother?.name ?: "Mère"})",
                        rabbit = motherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                }

                // ---- G4: ARRIÈRE-GRANDS-PARENTS ----
                Column(
                    modifier = Modifier.height(treeHeight).width(200.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GenealogyNodeCard(
                        label = "G4 : A-GP (Père de ${fatherFather?.name ?: "GP P."})",
                        rabbit = fatherFatherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GM (Mère de ${fatherFather?.name ?: "GP P."})",
                        rabbit = fatherFatherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GP (Père de ${fatherMother?.name ?: "GM P."})",
                        rabbit = fatherMotherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GM (Mère de ${fatherMother?.name ?: "GM P."})",
                        rabbit = fatherMotherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GP (Père de ${motherFather?.name ?: "GP M."})",
                        rabbit = motherFatherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GM (Mère de ${motherFather?.name ?: "GP M."})",
                        rabbit = motherFatherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GP (Père de ${motherMother?.name ?: "GM M."})",
                        rabbit = motherMotherFather,
                        defaultSex = "Male",
                        navController = navController
                    )
                    GenealogyNodeCard(
                        label = "G4 : A-GM (Mère de ${motherMother?.name ?: "GM M."})",
                        rabbit = motherMotherMother,
                        defaultSex = "Female",
                        navController = navController
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GenealogyNodeCard(
    label: String,
    rabbit: Rabbit?,
    defaultSex: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val exists = rabbit != null
    val isMale = if (exists) rabbit!!.sex == "Male" else defaultSex == "Male"

    val background = if (exists) {
        if (isMale) SoftMaleBlue else SoftFemalePink
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val contentColor = if (exists) {
        if (isMale) DarkMaleBlue else DarkFemalePink
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val borderStroke = if (exists) {
        BorderStroke(1.2.dp, contentColor.copy(alpha = 0.3f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = exists) {
                rabbit?.let { navController.navigate(Screen.RabbitDetail.createRoute(it.id)) }
            },
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = if (exists) 1.5.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            if (exists) {
                Text(
                    text = rabbit!!.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Race: ${rabbit.breed}",
                        fontSize = 9.sp,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${rabbit.weight} kg",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.9f)
                    )
                }
            } else {
                Text(
                    text = "Enregistrer ➕",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = contentColor,
                    modifier = Modifier.clickable {
                        navController.navigate(Screen.AddRabbit.route)
                    }
                )
                Text(
                    text = "Inconnu",
                    fontSize = 8.sp,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TreeConnectLine() {
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .height(28.dp)
    ) {
        val width = size.width
        val height = size.height

        // Draw dotted branched connecting trees
        drawLine(
            color = primaryColor,
            start = Offset(width * 0.25f, 0f),
            end = Offset(width * 0.25f, height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        drawLine(
            color = primaryColor,
            start = Offset(width * 0.75f, 0f),
            end = Offset(width * 0.75f, height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        drawLine(
            color = primaryColor,
            start = Offset(width * 0.25f, height / 2),
            end = Offset(width * 0.75f, height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        drawLine(
            color = primaryColor,
            start = Offset(width * 0.5f, height / 2),
            end = Offset(width * 0.5f, height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}
