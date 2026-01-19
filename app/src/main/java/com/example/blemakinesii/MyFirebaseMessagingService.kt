package com.example.blemakinesii

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM", "Token: $token")

        Firebase.database.reference
            .child("clients")
            .child("android")
            .setValue(token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: "Uyarı"
        val body = msg.notification?.body ?: ""

        // Normal Android Notification göster
    }
}
