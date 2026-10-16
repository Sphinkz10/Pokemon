#!/usr/bin/env python3
"""Static guard for safe Inbox triage. Device interactions are separately gated."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
base=root/"app/src/main/java/com/rui/pvpgo"
policy=(base/"InboxTriagePolicy.kt").read_text()
core=(base/"CoreScreens.kt").read_text()
overview=(base/"CollectionOverviewScreen.kt").read_text()
jvm=(root/"app/src/test/java/com/rui/pvpgo/InboxTriagePolicyTest.kt").read_text()
android=(root/"app/src/androidTest/java/com/rui/pvpgo/InboxTriageComposeAndroidTest.kt").read_text()
checks={
    "only PENDING candidates are classified": "it.status == CandidateInboxStatus.PENDING" in policy,
    "same existing reconciliation is reused": "CollectionReconciler.reconcile(item.candidate, collection)" in policy,
    "search never mutates the candidate collection": "val visible = candidates.filter" in policy,
    "missing data is unknown, not inferred": "owned.cp == null || owned.level == null" in policy,
    "reconciliation remains explicit on each card": "triage.reconciliationById[item.id]" in core,
    "search and saved filter selection": "var triageSearch by rememberSaveable" in core and "var triageFilter by rememberSaveable" in core,
    "filter chips expose real tests": "inbox-triage-${option.name.lowercase()}" in core,
    "can escape empty filters": 'testTag("inbox-triage-reset")' in core and 'triageSearch = ""' in core,
    "confirm remains explicit": 'testTag("inbox-confirm-${item.id}")' in core,
    "discard remains explicit": 'testTag("inbox-discard-${item.id}")' in core,
    "collection Inbox action stable": 'testTag("collection-open-inbox")' in overview,
    "eight safe JVM cases": jvm.count("@Test fun ")>=8,
    "five real Android UI cases": android.count("@Test fun ")>=5,
}
for label,ok in checks.items():print(("PASS " if ok else "FAIL ")+label)
assert all(checks.values()),"Inbox triage trust/UX regression"
print("INBOX_TRIAGE_SOURCE_CONTRACT",sum(checks.values()),"/",len(checks),"PASS")
