package com.dna.app.data.remote.functions

import com.dna.app.domain.model.DesignSpec
import com.dna.app.domain.taxonomy.Embellishment
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Neckline
import com.dna.app.domain.taxonomy.Occasion
import com.dna.app.domain.taxonomy.Silhouette
import com.dna.app.domain.taxonomy.SleeveStyle
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Typed wrapper around the server-side AI callables. Keeps DTO-mapping in one
 * place so ViewModels/UseCases never touch Firebase types directly.
 *
 * M3b: tagDress. M5 adds analyzeFabric / retrieveReferences / proposeDesignSpec /
 * generateDesign / editDesign.
 */
@Singleton
class AiFunctionsClient @Inject constructor(
    private val functions: FirebaseFunctions,
) {

    /**
     * Ask Gemini 2.5 Flash (server-side) to classify an uploaded dress and
     * persist the [DesignSpec] back onto `dresses/{dressId}`. Returns the spec
     * the server agreed on so the client can mirror it into Room immediately
     * without waiting on a Firestore round-trip.
     */
    suspend fun tagDress(dressId: String): DesignSpec {
        val result = functions
            .getHttpsCallable("tagDress")
            .call(mapOf("dressId" to dressId))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
            ?: error("tagDress: empty response")
        @Suppress("UNCHECKED_CAST")
        val spec = data["designSpec"] as? Map<String, Any?>
            ?: error("tagDress: missing designSpec")

        return DesignSpec(
            garmentType = GarmentType.valueOf(spec["garmentType"] as String),
            neckline = Neckline.valueOf(spec["neckline"] as String),
            sleeve = SleeveStyle.valueOf(spec["sleeve"] as String),
            silhouette = Silhouette.valueOf(spec["silhouette"] as String),
            occasion = Occasion.valueOf(spec["occasion"] as String),
            embellishments = (spec["embellishments"] as? List<*>).orEmpty()
                .mapNotNull { runCatching { Embellishment.valueOf(it as String) }.getOrNull() },
            dominantColors = (spec["dominantColors"] as? List<*>).orEmpty()
                .mapNotNull { it as? String },
        )
    }
}
