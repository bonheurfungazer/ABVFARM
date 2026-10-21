package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Rabbits : Screen("rabbits")
    object RabbitDetail : Screen("rabbit_detail/{rabbitId}") {
        fun createRoute(rabbitId: Int) = "rabbit_detail/$rabbitId"
    }
    object AddRabbit : Screen("add_rabbit")
    object Mating : Screen("mating")
    object Inventory : Screen("inventory")
    object Reminders : Screen("reminders")
    object Genealogy : Screen("genealogy/{rabbitId}") {
        fun createRoute(rabbitId: Int) = "genealogy/$rabbitId"
    }
    object FatteningBatches : Screen("fattening_batches")
    object Finances : Screen("finances")
}
