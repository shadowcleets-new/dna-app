package com.dna.app.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    @Provides @Singleton fun storage(): FirebaseStorage = FirebaseStorage.getInstance()

    /**
     * Cloud Functions in asia-south1 (Mumbai). Adjust per your Firebase region.
     * All AI pipeline calls route through here — the Gemini key never ships in
     * the APK.
     */
    @Provides @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
}
