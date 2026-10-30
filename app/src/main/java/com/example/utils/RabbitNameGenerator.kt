package com.example.utils

import java.util.Locale

/**
 * Intelligent and charming name generator for newborn kits (lapereaux).
 * Ensures every single rabbit receives a distinct, authentic proper name,
 * completely eliminating repetitive generic tags like "Lapereau n°1", "Lapereau n°2".
 */
object RabbitNameGenerator {

    private val CURATED_NAMES = listOf(
        // Gourmands, Épices & Douceurs
        "Caramel", "Noisette", "Pistache", "Cacao", "Moka", "Cannelle", "Praline",
        "Réglisse", "Vanille", "Cookie", "Nougat", "Brownie", "Châtaigne", "Truffe",
        "Gingembre", "Muffin", "Toast", "Safran", "Cerise", "Myrtille", "Amande",
        "Biscotte", "Chocolat", "Pepper", "Paprika", "Miel", "Sirocco", "Brioche",
        // Nobles, Légendes & Mythologie
        "Titan", "Orion", "Atlas", "Zeus", "César", "Sultan", "Ramsès", "Athéna",
        "Héra", "Vénus", "Calypso", "Olympe", "Hermès", "Apollon", "Thor", "Odin",
        "Sirius", "Phoenix", "Pégase", "Simba", "Nala", "Lancelot", "Arthur", "Merlin",
        "Goliath", "Achille", "Hector", "Ulysse", "Balthazar", "Darius", "Maximus",
        // Nature, Éléments & Pierres précieuses
        "Flocon", "Nuage", "Ébène", "Rubis", "Saphir", "Ambre", "Cristal", "Perle",
        "Jade", "Jaspe", "Quartz", "Émeraude", "Tempête", "Éclipse", "Blizzard", "Zéphyr",
        "Bambou", "Trèfle", "Basilic", "Sauge", "Jasmin", "Lupin", "Pollen", "Alba",
        "Kiwi", "Olive", "Brume", "Comète", "Étoile", "Aurore", "Cosmo", "Lilas",
        // Vifs, Chaleureux & Espiègles
        "Fripouille", "Filou", "Loustic", "Gaston", "Oscar", "Félix", "Milo", "Barnabé",
        "Domino", "Zigzag", "Bounty", "Shadow", "Flash", "Bandit", "Turbo", "Pixel",
        "Baloo", "Daisy", "Bella", "Luna", "Maya", "Stella", "Bonnie", "Paco", "Rio",
        "Kuzco", "Zorro", "Capucine", "Papillon", "Gaya", "Tango", "Grizzly", "Gavroche",
        "Blanchette", "Plume", "Rasta", "Volt", "Titus", "Pépito", "Bulle", "Pompom",
        "Neige", "Pépita", "Chicco", "Doudou", "Kiki", "Snoopy", "Zelda", "Nemo",
        "Django", "Balthazar", "Archibald", "Gulliver", "Céleste", "Samba", "Vasco"
    )

    /**
     * Generates a unique, non-repeating proper name for a kit.
     * Takes into account all existing rabbits currently on the farm as well
     * as kits already named within the current birth registration batch.
     */
    fun generateUniqueKitName(existingNames: Set<String>, usedInBatch: MutableSet<String>): String {
        val existingNormalized = existingNames.map { it.trim().lowercase(Locale.getDefault()) }.toSet()

        val available = CURATED_NAMES.filter { candidate ->
            val norm = candidate.trim().lowercase(Locale.getDefault())
            norm !in existingNormalized && norm !in usedInBatch
        }

        val chosen = if (available.isNotEmpty()) {
            available.random()
        } else {
            // If all curated names are already in the farm, pick a base name and add a Roman/number suffix
            val base = CURATED_NAMES.random()
            var counter = 2
            var candidate = "$base $counter"
            while (candidate.lowercase(Locale.getDefault()) in existingNormalized || candidate.lowercase(Locale.getDefault()) in usedInBatch) {
                counter++
                candidate = "$base $counter"
            }
            candidate
        }

        usedInBatch.add(chosen.trim().lowercase(Locale.getDefault()))
        return chosen
    }
}
