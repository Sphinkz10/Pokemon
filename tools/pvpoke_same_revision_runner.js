/*
 * Pokémon PvP Companion — PvPoke same-revision browser runner V1.29.
 *
 * Run only in a LOCAL PvPoke checkout previously verified by verify_pvpoke_checkout.py.
 * The verifier injects window.__POKEMON_PVP_PIN_PROOF__ before this script executes.
 * It refuses to export unless the exact commit/version proof is present.
 */
(async () => {
  "use strict";

  const EXPECTED = Object.freeze({
    schemaVersion: 2,
    commitSha: "a93147bf1f2e829758958bfb5b37e56bbadc9678",
    siteVersion: "1.40.1.3"
  });

  const proof = window.__POKEMON_PVP_PIN_PROOF__;
  if (!proof || proof.commitSha !== EXPECTED.commitSha || proof.siteVersion !== EXPECTED.siteVersion) {
    throw new Error("PIN_PROOF_MISMATCH: exact PvPoke checkout proof is required");
  }
  if (typeof Battle !== "function" || typeof Pokemon !== "function" || typeof GameMaster === "undefined") {
    throw new Error("PVPOKE_RUNTIME_NOT_READY: open a local PvPoke battle page first");
  }

  const cases = [
    {
      fixtureId: "PINNED-AZU-FERALIGATR",
      a: {speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},
      b: {speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:false,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}
    },
    {
      fixtureId: "PINNED-AZU-FERALIGATR-SHADOW",
      a: {speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},
      b: {speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:true,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}
    },
    {
      fixtureId: "PINNED-AZU-WALREIN",
      a: {speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},
      b: {speciesId:"walrein",level:20,ivs:[7,15,13],shadow:false,fast:"POWDER_SNOW",charged:["ICICLE_SPEAR","EARTHQUAKE"]}
    }
  ];

  function requireFn(obj, name, owner) {
    if (!obj || typeof obj[name] !== "function") throw new Error(`PVPOKE_API_MISMATCH: ${owner}.${name} missing`);
  }

  function configure(build, index, battle, shields) {
    const p = new Pokemon(build.speciesId, index, battle);
    for (const fn of ["setLevel","setIV","selectMove","setShields","initialize","setBattle","reset"]) requireFn(p, fn, "Pokemon");
    p.setLevel(build.level, false);
    p.setIV("atk", build.ivs[0]);
    p.setIV("def", build.ivs[1]);
    p.setIV("hp", build.ivs[2]);
    if (build.shadow) {
      requireFn(p, "setShadowType", "Pokemon");
      p.setShadowType("shadow");
    }
    p.selectMove("fast", build.fast);
    p.selectMove("charged", build.charged[0], 0);
    p.selectMove("charged", build.charged[1], 1);
    p.setShields(shields);
    // targetCP=false + isCustom=true preserves explicit level/IVs while recomputing stats/moves.
    p.initialize(false);
    battle.setNewPokemon(p, index, false);
    return p;
  }

  function jsonSafe(value) {
    if (value == null || typeof value === "string" || typeof value === "boolean") return value;
    if (typeof value === "number") return Number.isFinite(value) ? value : null;
    if (Array.isArray(value)) return value.map(jsonSafe);
    if (typeof value === "object") {
      const out = {};
      for (const k of Object.keys(value).sort()) out[k] = jsonSafe(value[k]);
      return out;
    }
    return String(value);
  }

  function normalizeTimeline(events) {
    return events.map((e, i) => ({
      i,
      turn: Number(e.turn ?? 0),
      time: Number(e.time ?? 0),
      actor: Number(e.actor ?? -1),
      type: String(e.type ?? ""),
      name: String(e.name ?? ""),
      values: jsonSafe(Array.isArray(e.values) ? e.values : []),
      editable: e.editable == null ? null : Boolean(e.editable)
    }));
  }

  async function sha256(text) {
    const data = new TextEncoder().encode(text);
    const digest = await crypto.subtle.digest("SHA-256", data);
    return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, "0")).join("");
  }

  const results = [];
  for (const c of cases) {
    const battle = new Battle();
    for (const fn of ["setCP","setLevelCap","setDecisionMethod","setBuffChanceModifier","setNewPokemon","simulate","getBattleRatings","getWinner","getTurns","getTurnsToWin"]) {
      requireFn(battle, fn, "Battle");
    }
    battle.setCP(1500);
    battle.setLevelCap(50);
    battle.setDecisionMethod("default");
    battle.setBuffChanceModifier(-1);
    const a = configure(c.a, 0, battle, 1);
    const b = configure(c.b, 1, battle, 1);
    const rawTimeline = battle.simulate() || [];
    const timeline = normalizeTimeline(rawTimeline);
    const ratings = battle.getBattleRatings().map(Number);
    const winnerObj = battle.getWinner();
    const winner = !winnerObj || winnerObj.pokemon === false || winnerObj.pokemon == null
      ? null
      : Number(winnerObj.pokemon.index);
    const turns = Number(battle.getTurns());
    const turnsToWin = (battle.getTurnsToWin() || []).map(Number);
    const traceCanonical = JSON.stringify(timeline);
    results.push({
      fixtureId: c.fixtureId,
      battleRatingA: ratings[0],
      battleRatingB: ratings[1],
      winner,
      turns,
      turnsToWin,
      final: {
        a: {hp:Number(a.hp), energy:Number(a.energy), shields:Number(a.shields), attackStage:Number(a.statBuffs?.[0] ?? 0), defenseStage:Number(a.statBuffs?.[1] ?? 0)},
        b: {hp:Number(b.hp), energy:Number(b.energy), shields:Number(b.shields), attackStage:Number(b.statBuffs?.[0] ?? 0), defenseStage:Number(b.statBuffs?.[1] ?? 0)}
      },
      traceSha256: await sha256(traceCanonical),
      timeline
    });
  }

  const payload = {
    schemaVersion: EXPECTED.schemaVersion,
    generatedAt: new Date().toISOString(),
    source: {
      repository: "https://github.com/pvpoke/pvpoke",
      commitSha: proof.commitSha,
      siteVersion: proof.siteVersion,
      verifiedBy: proof.verifiedBy || "verify_pvpoke_checkout.py"
    },
    scenario: {cp:1500, levelCap:50, shieldsA:1, shieldsB:1, decisionMethod:"default", buffChanceModifier:-1},
    results
  };

  const text = JSON.stringify(payload, null, 2);
  console.log("POKEMON_PVP_SAME_REVISION_EXPORT_START\n" + text + "\nPOKEMON_PVP_SAME_REVISION_EXPORT_END");
  try { await navigator.clipboard.writeText(text); console.log("Export copied to clipboard."); } catch (_) {}
  return payload;
})();
