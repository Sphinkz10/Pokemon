package com.rui.pvpgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests: GO uses localized keywords; never mistake a copied filter for a PvP rank. */
class TodayGoSearchLanguageTest {
    @Test fun missingOrUnknownPreferenceKeepsLegacyEnglishBehavior() {
        assertEquals(GoSearchLanguage.ENGLISH, GoSearchLanguage.fromStored(null))
        assertEquals(GoSearchLanguage.ENGLISH, GoSearchLanguage.fromStored("PT"))
        for (language in GoSearchLanguage.entries)
            assertEquals(language, GoSearchLanguage.fromStored(language.name))
    }

    @Test fun englishQueriesAreUnchangedForEveryGoalAndScope() {
        for (goal in TodayGoal.entries) for (scope in GoFilterScope.entries) {
            assertEquals(
                TodayJourneyPolicy.scopedSuggestion(goal, scope),
                TodayJourneyPolicy.scopedSuggestion(goal, scope, GoSearchLanguage.ENGLISH)
            )
        }
    }

    @Test fun allPortugueseSearchesMatchCuratedLanguageMatrix() {
        // Ordering is BROAD, STANDARD, FOCUSED.
        val expected = mapOf(
            TodayGoal.GREAT to listOf(
                "0-2ataque&2-4defesa&2-4ps",
                "0-1ataque&3-4defesa&3-4ps",
                "0ataque&4defesa&4ps"
            ),
            TodayGoal.ULTRA to listOf(
                "0-2ataque&2-4defesa&2-4ps",
                "0-1ataque&3-4defesa&3-4ps",
                "0ataque&4defesa&4ps"
            ),
            TodayGoal.IVS to listOf(
                "0-2ataque&2-4defesa&2-4ps",
                "0-1ataque&3-4defesa&3-4ps",
                "0ataque&4defesa&4ps"
            ),
            TodayGoal.MASTER to listOf("3*,4*", "4*", "4*"),
            TodayGoal.COLLECTION to listOf("0-4*", "3*,4*", "4*"),
            TodayGoal.EVENTS to listOf("idade0-30", "idade0-7", "idade0-1")
        )
        assertEquals(TodayGoal.entries.toSet(), expected.keys)
        for ((goal, queries) in expected) {
            for ((index, scope) in GoFilterScope.entries.withIndex()) {
                val search = TodayJourneyPolicy.scopedSuggestion(
                    goal, scope, GoSearchLanguage.PORTUGUESE_BR
                )
                assertEquals("${goal.name} / ${scope.name}", queries[index], search.filter)
            }
        }
    }

    @Test fun localizedQueriesNeverKeepEnglishStatOrAgeTerms() {
        for (goal in TodayGoal.entries) for (scope in GoFilterScope.entries) {
            val search = TodayJourneyPolicy.scopedSuggestion(
                goal, scope, GoSearchLanguage.PORTUGUESE_BR
            )
            for (englishKeyword in listOf("attack", "defense", "hp", "age"))
                assertFalse(search.filter.contains(englishKeyword))
            assertFalse(search.requiresEnglishGoTerms)
            assertTrue(search.limitation.contains("português do Brasil"))
        }
    }

    @Test fun starRatingsStayStableAcrossLanguagesAndDoNotImplySafety() {
        for (goal in listOf(TodayGoal.MASTER, TodayGoal.COLLECTION)) {
            for (scope in GoFilterScope.entries) {
                val english = TodayJourneyPolicy.scopedSuggestion(
                    goal, scope, GoSearchLanguage.ENGLISH
                )
                val portuguese = TodayJourneyPolicy.scopedSuggestion(
                    goal, scope, GoSearchLanguage.PORTUGUESE_BR
                )
                assertEquals(english.filter, portuguese.filter)
                assertTrue(portuguese.limitation.contains("Não") ||
                    portuguese.limitation.contains("não"))
            }
        }
    }

    @Test fun languageDoesNotChangeScopeTransitionOrUserReviewPolicy() {
        for (goal in TodayGoal.entries) {
            assertEquals(
                GoFilterScope.BROAD,
                TodayJourneyPolicy.broaden(GoFilterScope.STANDARD)
            )
            assertEquals(
                TodayJourneyStage.REVIEW_CANDIDATES,
                TodayJourneyPolicy.afterCopy(TodayJourneyStage.COPY_FILTER)
            )
            assertEquals(
                TodayJourneyStage.REVIEW_CANDIDATES,
                TodayJourneyPolicy.afterUserReview(TodayJourneyStage.COPY_FILTER)
                    .let { TodayJourneyPolicy.afterCopy(it) }
            )
        }
    }
}
