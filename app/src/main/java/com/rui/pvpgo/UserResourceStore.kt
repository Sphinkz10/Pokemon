package com.rui.pvpgo

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder

/**
 * Private, on-device preferences for deliberately entered resource balances.
 * Stored separately from Room Collection to avoid an unsafe schema migration.
 *
 * Global fields apply across Pokémon; regular and XL candies are scoped to the
 * named evolutionary family. No default numbers are persisted or inferred.
 */
internal class UserResourceStore(private val prefs: SharedPreferences) {
    private fun familyKey(familyId: String, kind: String): String =
        "family.${URLEncoder.encode(familyId, "UTF-8")}.$kind"

    private fun known(key: String): Int? {
        if (!prefs.contains(key)) return null
        return runCatching { prefs.getInt(key, -1) }.getOrNull()
            ?.takeIf { it >= 0 }
    }

    fun load(familyId: String): UserResourceBalances {
        require(familyId.isNotBlank())
        return UserResourceBalances(
            stardust = known("global.stardust"),
            candy = known(familyKey(familyId, "candy")),
            xlCandy = known(familyKey(familyId, "xl_candy")),
            fastTm = known("global.fast_tm"),
            chargedTm = known("global.charged_tm"),
            eliteFastTm = known("global.elite_fast_tm"),
            eliteChargedTm = known("global.elite_charged_tm")
        )
    }

    fun save(familyId: String, balances: UserResourceBalances) {
        require(familyId.isNotBlank())
        require(UserResourcePolicy.validated(balances)) {
            "Resources must be either unknown or non-negative"
        }
        val edit = prefs.edit()
        fun record(key: String, amount: Int?) {
            if (amount == null) edit.remove(key) else edit.putInt(key, amount)
        }
        record("global.stardust", balances.stardust)
        record(familyKey(familyId, "candy"), balances.candy)
        record(familyKey(familyId, "xl_candy"), balances.xlCandy)
        record("global.fast_tm", balances.fastTm)
        record("global.charged_tm", balances.chargedTm)
        record("global.elite_fast_tm", balances.eliteFastTm)
        record("global.elite_charged_tm", balances.eliteChargedTm)
        check(edit.commit()) { "Unable to persist user resources" }
    }

    companion object {
        fun get(context: Context): UserResourceStore = UserResourceStore(
            context.applicationContext.getSharedPreferences(
                "pvp_user_resources_v1", Context.MODE_PRIVATE
            )
        )
    }
}
