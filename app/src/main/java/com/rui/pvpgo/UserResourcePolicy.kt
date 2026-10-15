package com.rui.pvpgo

import com.rui.pvpgo.domain.ResourceLedger

/**
 * Nullable resources are truly unknown. A null balance never becomes zero;
 * zero is only valid when the player explicitly entered "0".
 *
 * Regular/XL candy here belongs to ONE explicit species-family key.
 * Other family balances remain unknown until recorded for those families.
 */
internal data class UserResourceBalances(
    val stardust: Int? = null,
    val candy: Int? = null,
    val xlCandy: Int? = null,
    val fastTm: Int? = null,
    val chargedTm: Int? = null,
    val eliteFastTm: Int? = null,
    val eliteChargedTm: Int? = null
) {
    val knownCount: Int get() = listOf(
        stardust, candy, xlCandy, fastTm, chargedTm, eliteFastTm, eliteChargedTm
    ).count { it != null }
}

internal object UserResourcePolicy {
    private const val MAX_BALANCE = 999_999_999

    /**
     * Inputs are either blank/unknown or an explicit non-negative integer.
     * Never interpret an invalid entry as zero or a missing balance.
     */
    fun parse(raw: String): Int? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return null
        if (normalized.length > 9 || !normalized.all(Char::isDigit)) return null
        return normalized.toIntOrNull()?.takeIf { it in 0..MAX_BALANCE }
    }

    fun isValidInput(raw: String): Boolean =
        raw.isBlank() || parse(raw) != null

    fun validated(balance: UserResourceBalances): Boolean =
        listOf(balance.stardust, balance.candy, balance.xlCandy,
            balance.fastTm, balance.chargedTm, balance.eliteFastTm,
            balance.eliteChargedTm).all { it == null || it in 0..MAX_BALANCE }

    /**
     * ResourceLedger has non-null defaults. Construct it ONLY after every
     * relevant field was explicitly confirmed, so missing data cannot be
     * confused with having no Stardust, Candy, XL or TMs.
     */
    fun completeLedgerOrNull(
        balances: UserResourceBalances,
        familyId: String
    ): ResourceLedger? {
        if (familyId.isBlank() || !validated(balances)) return null
        val dust = balances.stardust ?: return null
        val candy = balances.candy ?: return null
        val xl = balances.xlCandy ?: return null
        val fast = balances.fastTm ?: return null
        val charged = balances.chargedTm ?: return null
        val eliteFast = balances.eliteFastTm ?: return null
        val eliteCharged = balances.eliteChargedTm ?: return null
        return ResourceLedger(
            stardust = dust,
            candyByFamily = mapOf(familyId to candy),
            xlCandyByFamily = mapOf(familyId to xl),
            fastTm = fast,
            chargedTm = charged,
            eliteFastTm = eliteFast,
            eliteChargedTm = eliteCharged
        )
    }

    const val SCOPE_NOTE =
        "Estes valores são introduzidos manualmente e podem estar desatualizados. " +
            "Candy e XL são específicos desta família; outras famílias continuam desconhecidas. " +
            "Ter os recursos registados não significa que o build seja viável nem que compense investir."
}
