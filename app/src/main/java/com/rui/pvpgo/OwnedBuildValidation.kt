package com.rui.pvpgo

/** No guessed IV, CP or level values: empty fields mean UNKNOWN. */
object OwnedBuildValidation {
    /** A typed-but-unparseable CP must never silently become unknown CP. */
    fun cpError(raw: String): String? {
        if (raw.isBlank()) return null
        val cp = raw.toIntOrNull() ?: return "CP inválido. Introduz um número válido."
        return if (cp in 10..10000) null else "O CP deve estar entre 10 e 10 000."
    }

    /** Levels advance by 0.5 up to current Pokémon GO cap. */
    fun levelError(raw: String): String? {
        if (raw.isBlank()) return null
        val value = raw.replace(',', '.').toDoubleOrNull()
            ?: return "Nível inválido. Exemplo: 23,5."
        if (!value.isFinite() || value !in 1.0..51.0) {
            return "O nível tem de estar entre 1 e 51."
        }
        if (value * 2 != (value * 2).toInt().toDouble()) {
            return "O nível deve avançar em passos de 0,5."
        }
        return null
    }

    /**
     * Reject newly selected attacks not in the species' known move lists.
     * Previously saved legacy/event attacks are tolerated unchanged, because
     * external catalogs can be incomplete; this prevents accidental data loss.
     * An empty allowed pool means no authoritative list has been loaded.
     */
    fun movePoolError(
        fastMoveId: String?,
        chargedMoveIds: List<String>,
        allowedFastIds: Set<String>,
        allowedChargedIds: Set<String>,
        previousFastId: String?,
        previousChargedIds: List<String>
    ): String? {
        if (fastMoveId != null && allowedFastIds.isNotEmpty() &&
            fastMoveId !in allowedFastIds && fastMoveId != previousFastId
        ) return "Este ataque rápido não consta dos dados disponíveis para a espécie."
        if (allowedChargedIds.isNotEmpty() &&
            chargedMoveIds.any { it !in allowedChargedIds && it !in previousChargedIds }
        ) return "Foi selecionado um ataque carregado que não consta dos dados disponíveis."
        return null
    }

    fun chargedMovesError(moveIds: List<String>): String? = when {
        moveIds.size > 2 -> "Só podes selecionar até 2 ataques carregados."
        moveIds.distinct().size != moveIds.size -> "Os ataques carregados não podem repetir-se."
        else -> null
    }
}
