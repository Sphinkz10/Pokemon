#!/usr/bin/env python3
"""Required audit: Android tests are executed on emulator, not counted from source alone."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[2]
android_test=root/"app/src/androidTest/java/com/rui/pvpgo"
required={
    "SavedTeamRoomIntegrationTest":6,
    "UserDatabaseMigrationTest":1,
    "UserResourceStoreAndroidTest":4,
    "InvestmentJourneyAndroidTest":7,
    "MainActivityLaunchAndroidTest":1,
    "InvestmentComposeUiAndroidTest":12,
    "CollectionNavigationJourneyAndroidTest":8,
    "MainActivityLifecycleA11yAndroidTest":4,
    "PersistentCollectionReopenAndroidTest":8,
    "ResourceResponsiveConstraintsAndroidTest":3,
    "ActualApkUpgradeAndroidTest":1,
    "CollectionGuidedIvAndroidTest":3,
    "PvpGuidedCollectionComposeAndroidTest":8,
    "OwnedIvCompareComposeAndroidTest":4,
    "CollectionPlanJourneyAndroidTest":18,
    "PokemonGoSearchComposeAndroidTest":5,
    "PokemonGoCandidateHandoffAndroidTest":7,
    "InboxTriageComposeAndroidTest":7,
    "InboxRestoreAndroidTest":5,
}

def source_contract():
    workflow=(root/".github/workflows/android-room-instrumentation.yml").read_text()
    checks={
        "PRs run real emulator": "pull_request:" in workflow and "reactivecircus/android-emulator-runner@v2" in workflow,
        "Android 35 system image": "api-level: 35" in workflow and "arch: x86_64" in workflow,
        "Gradle connected instrumentation task": ":app:connectedDebugAndroidTest" in workflow,
        "CI checks genuine test reports": "--verify-results" in workflow,
        "upload raw device reports even on failure": "if: always()" in workflow and "androidTests" in workflow,
        "launcher smoke runs on Android": "ActivityScenario.launch(MainActivity::class.java)" in (android_test/"MainActivityLaunchAndroidTest.kt").read_text(),
        "resource journey uses actual Room": "Room.inMemoryDatabaseBuilder" in (android_test/"InvestmentJourneyAndroidTest.kt").read_text(),
        "resource journey uses Android preferences": 'getSharedPreferences("journey-android-test-v1"' in (android_test/"InvestmentJourneyAndroidTest.kt").read_text(),
        "Compose UI harness included": 'androidTestImplementation("androidx.compose.ui:ui-test-junit4")' in (root/"app/build.gradle.kts").read_text(),
        "resource form exposed with stable UI semantics": all(tag in (root/"app/src/main/java/com/rui/pvpgo/UserResourcePanel.kt").read_text() for tag in [
            'testTag("resource-toggle")','testTag("resource-save")','testTag("resource-input-${field.name.lowercase()}")'
        ]),
        "investment target keeps meaningful UI test anchors": all(tag in (root/"app/src/main/java/com/rui/pvpgo/InvestmentTargetPanel.kt").read_text() for tag in [
            'testTag("community-estimate-consent")','testTag("cap-target-button")','testTag("affordable-target-button")'
        ]),
        "UI tests perform real clicks typing clearing": all(token in (android_test/"InvestmentComposeUiAndroidTest.kt").read_text() for token in [
            'createComposeRule()', 'performClick()', 'performTextInput(', 'performTextClearance()', 'assertIsNotEnabled()'
        ]),
        "root app routes are actually exercised": all(token in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text() for token in [
            "PvPGoApp(", 'app-tab-collection', 'owned-row-nav-owned-one',
            'species-owned-nav-owned-one', 'pokemon-360-back',
            'Descartar alterações', 'Continuar a editar'
        ]),
        "isolation is an explicit real Room repository": "CollectionRepository.forTesting(database)" in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text() and "Room.inMemoryDatabaseBuilder" in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text(),
        "app defaults to production catalog": "catalogOverride ?: emptyList()" in (root/"app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text(),
        "app defaults to production repository": "repositoryOverride ?: CollectionRepository.get(context)" in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text(),
        "lifecycle instrumentation runs on real activity": all(
            token in (android_test/"MainActivityLifecycleA11yAndroidTest.kt").read_text()
            for token in [
                "createAndroidComposeRule<MainActivity>()",
                ".scenario.recreate()", "moveToState(Lifecycle.State.CREATED)",
                "SCREEN_ORIENTATION_LANDSCAPE", "assertContentDescriptionEquals"
            ]
        ),
        "navigation exposes accessible selected tab state": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text()
            for token in [".selectable(", "selected = selected", "role = Role.Tab",
                          "contentDescription = tab.label"]
        ),
        "unsaved resources use explicit Android Bundle saver": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/UserResourcePanel.kt").read_text()
            for token in ["resourceDraftSaver:", "Saver(", "rememberSaveable(familyId, saved, saver = resourceDraftSaver)"]
        ),
        "resource UI tests restore state and exercise 200 percent fonts": all(
            token in (android_test/"InvestmentComposeUiAndroidTest.kt").read_text()
            for token in ["StateRestorationTester(rule)", "emulateSavedInstanceStateRestore()",
                          "fontScale = 2f"]
        ),
        "named on-disk Room fixture must survive actual close and reopen": all(
            token in (android_test/"PersistentCollectionReopenAndroidTest.kt").read_text()
            for token in ["Room.databaseBuilder(", "context.deleteDatabase(filename)",
                          ".addMigrations(PvpUserDatabase.MIGRATION_1_2)",
                          "fun wholeOwnedSpecimenSurvivesActualFileCloseAndReopen",
                          "fun largeCollectionCanBeReopenedWithStableIdsAndNoDataLoss"]
        ),
        "responsive form exercises narrow and wide actual Compose constraints": all(
            token in (android_test/"ResourceResponsiveConstraintsAndroidTest.kt").read_text()
            for token in ["widthDp = 320, fontScale = 2f", "widthDp = 390, fontScale = 1.5f",
                          "widthDp = 600, fontScale = 1f", "performScrollTo().performClick()"]
        ),
        "guided IV stages exercise real Compose controls": all(
            token in (android_test/"CollectionGuidedIvAndroidTest.kt").read_text()
            for token in [
                "createComposeRule()", "collection-guide-review",
                "collection-guide-compare", "collection-guide-plan",
                "collection-guide-open-iv-iv-ready", "performClick()"
            ]
        ),
        "guided collection stage is restored and routes to existing IV targets": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in [
                "var guidedStep by rememberSaveable",
                "CollectionGuidePolicy.matches(", "CollectionJourneyGuide(",
                "onOpenIvTargets(owned.speciesId)",
                "testTag(\"collection-guide-open-iv-${owned.id}\")"
            ]
        ),
        "integrated PvP league guide has real UI test taps": all(
            token in (android_test/"PvpGuidedCollectionComposeAndroidTest.kt").read_text()
            for token in ["CollectionOverviewScreen(", "collection-guide-compare",
                          "pvp-compare-stage-over_limit", "pvp-compare-stage-iv_pattern",
                          "collection-guide-open-iv-guide-low", "performClick()"]
        ),
        "single existing IV guide remains the only learning journey": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in ["CollectionJourneyGuide(", "pvpCompareStage",
                          "PvpGuidePolicy.matches(", 'pvp-compare-league-${option.name.lowercase()}']
        ),
        "CP checks do not invent data or ranks": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/PvpGuidePolicy.kt").read_text()
            for token in ["owned.uncertainFields.isNotEmpty()", "expectedCp != cp",
                          "PvpGuideVerdict.NEEDS_REVIEW", "Não é ranking IV"]
        ),
        "selected IV guide league travels to actual IV Targets route": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in ["onOpenIvTargetsForLeague = { speciesId, selectedLeague ->",
                          "speciesLeague = selectedLeague",
                          "targetsReturnRoute = CollectionRoute.LIST"]
        ),
        "guide labels chosen league and preserves older one-argument callers": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in ["onOpenIvTargetsForLeague: ((String, League) -> Unit)? = null",
                          "onOpenIvTargetsForLeague(owned.speciesId, selectedLeague)",
                          "onOpenIvTargets(owned.speciesId)",
                          "Comparar IVs ·"]
        ),
        "UI verifies Ultra and Master in destination not only callback": all(
            token in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text()
            for token in ["iv-targets-league", "pvp-compare-league-ultra",
                          "pvp-compare-league-master", "iv-targets-back"]
        ),
        "guide empty selection has actionable reset and honest explanation": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in [
                "recoverEmptySelection", 'testTag("collection-guide-reset-empty")',
                'testTag("collection-guide-empty-explanation")',
                '"LIMPAR SELEÇÃO"', "pvpCompareStage = PvpGuideStage.ALL"
            ]
        ),
        "real Compose reset tests protect safe recovery": all(
            token in (android_test/"PvpGuidedCollectionComposeAndroidTest.kt").read_text()
            for token in [
                "impossibleLeagueCapSelectionOffersSafeResetAndReturnsAllOwnedPokemon",
                "noSavedPlansHasPracticalExitInsteadOfAnUnreachableEmptyStage",
                "nonEmptyGuidedSelectionNeverDisplaysIrrelevantResetButton"
            ]
        ),
        "own IV rank reflects potential and protects unknown CP": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/OwnedIvDecisionPolicy.kt").read_text()
            for token in ["PvpGuidePolicy.assess(", "it.rank?.rank ?: Int.MAX_VALUE",
                          "candidate.readiness.verdict == PvpGuideVerdict.NEEDS_REVIEW",
                          "CP atual acima do limite"]
        ),
        "IV ranking shows exact saved owned rows with safe route": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/IvTargetsScreen.kt").read_text()
            for token in ['testTag("iv-owned-row-${owned.id}")',
                          'testTag("iv-owned-open-${owned.id}")',
                          "onOpenOwned: ((String) -> Unit)? = null",
                          "OwnedIvDecisionPolicy.sort(", "showAllOwned"]
        ) and all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in ["detailReturnRoute = CollectionRoute.IV_TARGETS",
                          "selectedOwnedId = ownedId",
                          "it.speciesId == species.speciesId"]
        ),
        "new Compose tests open exact owned and never alter unknown CP": all(
            token in (android_test/"OwnedIvCompareComposeAndroidTest.kt").read_text()
            for token in ["OwnedIvCompareComposeAndroidTest", "iv-owned-toggle-all",
                          "iv-owned-status-d", "iv-owned-open-d", "assertEquals(null, owned.last().cp)"]
        ),
        "full app goes IV targets to 360 and back to chosen league": all(
            token in (android_test/"CollectionNavigationJourneyAndroidTest.kt").read_text()
            for token in ["leagueIvTargetsCanOpenOwnedPokemon360AndReturnToSameLeague",
                          "iv-owned-open-nav-owned-one", "iv-targets-league"]
        ),
        "plan journey uses real collection, explicit draft and real saved Room": all(
            token in (android_test/"CollectionPlanJourneyAndroidTest.kt").read_text()
            for token in [
                "CollectionOverviewScreen(", "PvPGoApp(", "onStartPlan =",
                "collection-plan-start-plan-owned", "pokemon-360-save-plan",
                "repository.buildPlans.first()", "repository.currentCollection()"
            ]
        ),
        "plan entry never automatically saves a plan": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in [
                "onStartPlan: ((String, League) -> Unit)? = null",
                "collection-plan-start-", "onStartPlan(owned.id, pvpCompareLeague)"
            ]
        ),
        "guide opens direct 360 and scrolls actual plan editor": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in [
                "focusedPlanOwnedId = ownedId", "route = CollectionRoute.DETAIL",
                "editorListState.scrollToItem(", 'testTag("pokemon-360-save-plan")'
            ]
        ),
        "saved plan next action visible on actual collection UI": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in [
                "SavedPlanNextActionPolicy.next(", 'testTag("collection-plan-next-',
                'testTag("collection-plan-open-next-', "resourceStoreOverride ?: UserResourceStore.get(context)"
            ]
        ),
        "no fabricated meta or affordability approval": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/SavedPlanNextActionPolicy.kt").read_text()
            for token in [
                "balances.stardust == null", "balances.candy == null", "balances.xlCandy == null",
                "InvestmentReadinessPolicy.inspect(", "CAVEAT",
                "não prova que sejam atuais nem suficientes"
            ]
        ),
        "10 pure policy tests explicitly cover unknown vs zero": all(
            token in (root/"app/src/test/java/com/rui/pvpgo/SavedPlanNextActionPolicyTest.kt").read_text()
            for token in ["unknownRelevantResourcesAreListedWithoutInventingZero",
                          "zeroResourcesDoNotMeanAffordabilityOrPermissionToSpend"]
        ),
        "saved plan navigation carries true league and actionable step": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in ["onOpenSavedPlanAction = { ownedId, planLeague, nextStep ->",
                          "speciesLeague = planLeague",
                          "focusedNextPlanStep = nextStep",
                          "SavedPlanFocusPolicy.targetIndex(focusNextPlanStep, count)"]
        ),
        "saved plan Android tests exercise real shortcuts": all(
            token in (android_test/"CollectionPlanJourneyAndroidTest.kt").read_text()
            for token in ["savedPlanActionHandsExactOwnedLeagueAndTaskToNavigation",
                          "realAppSavedUltraPlanOpensOwnMoveEditorWithUltraLeagueAndReturnsToGuide",
                          "realAppMissingCpJumpsToCpEditorWithoutWritingEstimatedValues"]
        ),
        "plan guide filters by selected league": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text()
            for token in [
                "CollectionPlanLeaguePolicy.preferredByOwned(buildPlans, pvpCompareLeague)",
                "CollectionPlanLeaguePolicy.hasNonArchivedPlanInLeague(",
                "owned.id, pvpCompareLeague, buildPlans",
                "val savedPlan = selectedLeaguePlans[owned.id]",
                "hasSavedPlan = visiblePlanByOwned.containsKey(owned.id)"
            ]
        ),
        "guide league tests are actual Compose clicks": all(
            token in (android_test/"CollectionPlanJourneyAndroidTest.kt").read_text()
            for token in [
                "greatGuideWithOnlyUltraPlanOffersGreatCreationInsteadOfWrongAction",
                "selectingUltraShowsSavedUltraPlanAndHidesCreateButton",
                "higherPriorityGreatPlanCannotHijackUltraSavedAction",
                "pvp-compare-league-"
            ]
        ),
        "real Pokémon GO search clipboard UI tested": all(
            token in (android_test/"PokemonGoSearchComposeAndroidTest.kt").read_text()
            for token in [
                "PokemonGoSearchPanel(", "go-search-copy", "clipText()",
                "go-search-language-pt", "collection-guide-compare"
            ]
        ),
        "GO clipboard UI is present in existing comparison flow": "PokemonGoSearchPanel(\n                league = compareLeague," in
            (root/"app/src/main/java/com/rui/pvpgo/CollectionOverviewScreen.kt").read_text(),
        "GO candidates are manually entered into an actual isolated Room Inbox": all(
            token in (android_test/"PokemonGoCandidateHandoffAndroidTest.kt").read_text()
            for token in [
                "PvPGoApp(", "Room.inMemoryDatabaseBuilder",
                "go-search-record-candidate", "go-search-review-inbox",
                "manual-import-species-search", "manual-import-iv-ataque",
                "manual-import-submit", "repository.pendingInbox.first()",
                "repository.currentCollection()"
            ]
        ),
        "GO filter guide has direct real destinations without auto-import": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in [
                "onRecordCandidates = {", "route = CollectionRoute.MANUAL_GUIDED",
                "manualReturnRoute = CollectionRoute.LIST"
            ]
        ),
        "Inbox UI exposes unique per-candidate actions": all(
            tag in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for tag in [
                'testTag("inbox-candidate-${item.id}")',
                'testTag("inbox-preview-${item.id}")',
                'testTag("inbox-confirm-${item.id}")',
                'testTag("inbox-discard-${item.id}")'
            ]
        ),
        "Inbox E2E covers confirmed and discarded data without silent promotion": all(
            token in (android_test/"PokemonGoCandidateHandoffAndroidTest.kt").read_text()
            for token in [
                "fun candidateIvPreviewOpensAndClosesWithoutAutoAddingOwnedPokemon()",
                "fun explicitlyDiscardedCandidateNeverEntersOwnedCollection()",
                "fun explicitConfirmCreatesExactOwnedPokemonAndOpensReal360()",
                'assertEquals("CONFIRMED_NEW", reviewed.status.name)',
                'assertEquals("DISCARDED", reviewed.status.name)',
                'pokemon-360-back'
            ]
        ),
        "Inbox triage is real Android Room, not an invented result": all(
            item in (android_test/"InboxTriageComposeAndroidTest.kt").read_text()
            for item in ["Room.inMemoryDatabaseBuilder", "repository.pendingInbox.first()",
                         'filter("possible_matches")', "inbox-triage-reset",
                         "inbox-discard-triage-different", "performClick()"]
        ),
        "Inbox filtering cannot silently mutate saved Pokémon": all(
            item in (root/"app/src/main/java/com/rui/pvpgo/InboxTriagePolicy.kt").read_text()
            for item in ["CandidateInboxStatus.PENDING", "CollectionReconciler.reconcile(",
                         "ReconciliationReport", "matchesCategory"]
        ),
        "discard requires explicit confirmation and opt-in Undo": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text()
            for token in ['testTag("inbox-discard-confirm")',
                          'testTag("inbox-discard-cancel")',
                          'actionLabel = "Desfazer"',
                          'repository.restoreDiscarded(item.id)']
        ),
        "Undo is transactional and refuses confirmed or linked items": all(
            token in (root/"app/src/main/java/com/rui/pvpgo/CollectionDatabase.kt").read_text()
            for token in ['suspend fun restoreDiscarded(inboxId: String)',
                          'row.status == CandidateInboxStatus.DISCARDED.name',
                          'dao.ownedById(row.candidateOwnedId) == null',
                          'status = CandidateInboxStatus.PENDING.name']
        ),
        "source test count minimum": all(
            (android_test/f"{name}.kt").read_text().count("@Test fun ") >= count
            for name,count in required.items()
        ),
    }
    for label,ok in checks.items(): print(("PASS " if ok else "FAIL ")+label)
    assert all(checks.values()),"Missing Android instrumentation guard"
    print("ANDROID_SOURCE_CONTRACT",len(checks),"/",len(checks),"PASS")

def verify_results():
    folder=root/"app/build/outputs/androidTest-results/connected"
    files=list(folder.rglob("*.xml"))
    if not files:
        raise AssertionError(f"No real emulator XML reports under {folder}")
    seen={name:{"tests":0,"failed":0} for name in required}
    unique_cases=set()
    unknown=[]
    for file in files:
        try:
            parsed=ET.parse(file).getroot()
        except ET.ParseError as error:
            raise AssertionError(f"Unreadable real Android XML report: {file}: {error}")
        # Android Gradle Plugin may combine multiple classes under ONE
        # <testsuite name='first suite'>. Always use each individual
        # <testcase classname='com.rui.pvpgo.ActualSuite'> instead.
        for case in parsed.iter("testcase"):
            classname=case.get("classname","")
            name=classname.split(".")[-1]
            if name not in required:
                unknown.append(classname)
                continue
            case_id=(classname,case.get("name",""))
            if case_id in unique_cases:
                raise AssertionError(f"Duplicate Android test case in reports: {case_id}")
            unique_cases.add(case_id)
            seen[name]["tests"]+=1
            if (case.find("failure") is not None or
                case.find("error") is not None or
                case.find("skipped") is not None):
                seen[name]["failed"]+=1
    for name,minimum in required.items():
        value=seen[name]
        print(f"EMULATOR_TEST {name}: {value['tests']}/{minimum} cases; {value['failed']} failures/skips")
        assert value["tests"] >= minimum and value["failed"] == 0, (
            f"Android test suite missing/failed: {name}"
        )
    assert not unknown, f"Unknown instrumented test classes require review: {unknown}"
    assert len(unique_cases)>=sum(required.values()), "Insufficient executed Android cases"
    print(f"ANDROID_INSTRUMENTATION_RESULT {len(unique_cases)} real emulator cases across {len(required)} suites")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("--check-source","--verify-results"):
        raise SystemExit("Usage: verify_instrumentation.py --check-source|--verify-results")
    if sys.argv[1]=="--check-source": source_contract()
    else: verify_results()
