package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    e: Exception,
    operationType: OperationType,
    path: String
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val authContext = JSONObject().apply {
        put("uid", currentUser?.uid)
        put("email", currentUser?.email)
    }
    val logPayload = JSONObject().apply {
        put("event", "firestore_error")
        put("operation", operationType.value)
        put("path", path)
        put("error_message", e.message)
        put("auth_context", authContext)
    }
    Log.e("FirestoreError", logPayload.toString())
}
