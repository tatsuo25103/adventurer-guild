package com.example.adventurerguild.data

import com.example.adventurerguild.model.UserProfile
import com.example.adventurerguild.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    suspend fun register(email: String, password: String, displayName: String, asAdmin: Boolean): UserProfile {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Firebase registration returned no user.")
        val profile = UserProfile(
            uid = user.uid,
            email = email,
            displayName = displayName,
            role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
        )
        db.collection(FirestoreCollections.USERS).document(user.uid)
            .set(profile.toFirestoreMap())
            .await()
        return profile
    }

    suspend fun login(email: String, password: String): UserProfile {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Firebase login returned no user.")
        return loadProfile(uid)
    }

    suspend fun loginWithGoogle(idToken: String, asAdmin: Boolean): UserProfile {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val firebaseUser = result.user ?: error("Firebase Google login returned no user.")
        val userRef = db.collection(FirestoreCollections.USERS).document(firebaseUser.uid)
        val existing = userRef.get().await()
        if (existing.exists()) {
            val profile = existing.toUserProfile().copy(
                role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
            )
            userRef.update("role", profile.role.name).await()
            return profile
        }

        val profile = UserProfile(
            uid = firebaseUser.uid,
            email = firebaseUser.email.orEmpty(),
            displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@").orEmpty(),
            role = if (asAdmin) UserRole.GUILD_ADMIN else UserRole.ADVENTURER
        )
        userRef.set(profile.toFirestoreMap()).await()
        return profile
    }

    suspend fun loadCurrentProfile(): UserProfile? =
        currentUid?.let { loadProfile(it) }

    suspend fun loadProfile(uid: String): UserProfile {
        val snapshot = db.collection(FirestoreCollections.USERS).document(uid).get().await()
        return snapshot.toUserProfile()
    }

    suspend fun updateCustomTitle(uid: String, title: String) {
        db.collection(FirestoreCollections.USERS).document(uid)
            .update("customTitle", title.trim())
            .await()
    }

    suspend fun assignGuildRole(uid: String, guildId: String, roleTitle: String) {
        db.collection(FirestoreCollections.USERS).document(uid)
            .update(FieldPath.of("guildRoles", guildId), roleTitle.trim())
            .await()
    }

    suspend fun listGuildMembers(guildId: String): List<UserProfile> {
        val joined = db.collection(FirestoreCollections.USERS)
            .whereArrayContains("joinedGuildIds", guildId)
            .get()
            .await()
            .documents
            .map { it.toUserProfile() }
        val admins = db.collection(FirestoreCollections.USERS)
            .whereArrayContains("managedGuildIds", guildId)
            .get()
            .await()
            .documents
            .map { it.toUserProfile() }
        return (joined + admins).distinctBy { it.uid }.sortedBy { it.displayName }
    }

    fun logout() {
        auth.signOut()
    }
}
