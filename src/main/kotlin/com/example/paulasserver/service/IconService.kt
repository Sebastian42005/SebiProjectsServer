package com.example.paulasserver.service

import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

// ---------- DTOs ----------

data class LibreTranslateResponse(
    val translatedText: String? = null
)

data class IconifySearchResponse(
    val icons: List<String> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val start: Int = 0
)

// ---------- Service ----------

@Service
class IconUrlService {
    private val libreBaseUrl: String = "http://localhost:5001"
    private val iconifyBaseUrl: String = "https://api.iconify.design"
    private val webClient: WebClient = WebClient.builder().build()

    // Icon-Set-Prio: nimm gern deine Favoriten dazu/raus
    private val preferredPrefixes = listOf(
        "emojione-v1",
        "fluent-emoji-flat",
        "twemoji",
        "noto",
        "mdi",          // Material Design Icons (riesig)
        "ph",           // Phosphor
        "tabler",       // Tabler Icons
        "fa6-solid",    // FontAwesome 6 Solid
        "lucide"        // Lucide
    )

    private val translationOverrides: Map<String, String> = mapOf(
        "speck" to "bacon",
        "apfel" to "apple",
        "butter" to "butter",
        "käse" to "cheese",
        "milch" to "milk",
        "banane" to "banana",
        "kartoffel" to "potato",
        "karotte" to "carrot",
        "eier" to "eggs",
        "ei" to "egg",
        "tomate" to "tomato",
        "schinken" to "ham",
        "salz" to "salt",
        "zucker" to "sugar",
        "reis" to "rice",
        "nudeln" to "pasta",
        "fisch" to "fish",
        "huhn" to "chicken"
    )

    fun iconSvgUrlForGermanTerm(termDe: String): String {
        val clean = termDe.trim()
        require(clean.isNotBlank()) { "term is blank" }

        val en = translateDeToEn(clean)
            .map { it.ifBlank { clean } }
            .block() ?: clean

        val searchResponse = searchIconify(en)
            .block()

        val iconName = pickBestIcon(searchResponse?.icons ?: emptyList(), en)

        return "$iconifyBaseUrl/$iconName.svg"
    }

    private fun translateDeToEn(text: String): Mono<String> {
        val key = text.trim().lowercase()
        if (key.isBlank()) return Mono.just("")

        // 1) Override / Whitelist
        translationOverrides[key]?.let { return Mono.just(it) }
        // LibreTranslate: POST /translate (form-encoded)
        // q=...&source=de&target=en
        return webClient.post()
            .uri("$libreBaseUrl/translate")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(
                BodyInserters
                    .fromFormData("q", text)
                    .with("source", "de")
                    .with("target", "en")
            )
            .retrieve()
            .bodyToMono(LibreTranslateResponse::class.java)
            .map { it.translatedText?.trim().orEmpty() }
    }

    private fun searchIconify(query: String): Mono<IconifySearchResponse> {
        val url = UriComponentsBuilder
            .fromUriString("$iconifyBaseUrl/search")
            .queryParam("query", query)
            .queryParam("limit", 48)
            .build()
            .toUriString()

        return webClient.get()
            .uri(url)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(IconifySearchResponse::class.java)
    }

    private fun pickBestIcon(icons: List<String>, name: String): String {
        if (icons.isEmpty()) {
            // Fallback-Icon (du kannst hier was anderes nehmen)
            return "mdi:help-circle-outline"
        }

        val exactMatchIcons = icons.filter {
            it.split(':')[1].trim().equals(name.trim(), ignoreCase = true)
        }

        if (exactMatchIcons.isNotEmpty()) {
            for (prefix in preferredPrefixes) {
                val match = exactMatchIcons.firstOrNull { it.startsWith("$prefix:") }
                if (match != null) return match
            }
            return exactMatchIcons[0]
        }

        // 1) bevorzugte Prefixe zuerst
        for (prefix in preferredPrefixes) {
            val match = icons.firstOrNull { it.startsWith("$prefix:") }
            if (match != null) return match
        }

        // 2) sonst einfach das erste
        return icons.first()
    }
}
