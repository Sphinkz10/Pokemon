/* Pokémon PvP Companion V1.29 — F46 expanded exact-pin browser runner. */
(async () => {
  "use strict";
  const EXPECTED={schemaVersion:2,commitSha:"a93147bf1f2e829758958bfb5b37e56bbadc9678",siteVersion:"1.40.1.3"};
  const proof=window.__POKEMON_PVP_PIN_PROOF__;
  if(!proof||proof.commitSha!==EXPECTED.commitSha||proof.siteVersion!==EXPECTED.siteVersion) throw new Error("PIN_PROOF_MISMATCH");
  if(typeof Battle!=="function"||typeof Pokemon!=="function"||typeof GameMaster==="undefined") throw new Error("PVPOKE_RUNTIME_NOT_READY");

  const cases=[
    {fixtureId:"PINNED-AZU-FERALIGATR",shieldsA:1,shieldsB:1,a:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},b:{speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:false,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}},
    {fixtureId:"PINNED-AZU-FERALIGATR-SHADOW",shieldsA:1,shieldsB:1,a:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},b:{speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:true,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}},
    {fixtureId:"PINNED-AZU-WALREIN",shieldsA:1,shieldsB:1,a:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},b:{speciesId:"walrein",level:20,ivs:[7,15,13],shadow:false,fast:"POWDER_SNOW",charged:["ICICLE_SPEAR","EARTHQUAKE"]}},
    {fixtureId:"EXP-AZU-FERALIGATR-0S",shieldsA:0,shieldsB:0,a:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},b:{speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:false,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}},
    {fixtureId:"EXP-AZU-FERALIGATR-2S",shieldsA:2,shieldsB:2,a:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]},b:{speciesId:"feraligatr",level:19.5,ivs:[6,15,6],shadow:false,fast:"SHADOW_CLAW",charged:["HYDRO_CANNON","ICE_BEAM"]}},
    {fixtureId:"EXP-ALTARIA-AZU-1TURN",shieldsA:1,shieldsB:1,a:{speciesId:"altaria",level:28.5,ivs:[4,12,13],shadow:false,fast:"DRAGON_BREATH",charged:["SKY_ATTACK","DRAGON_PULSE"]},b:{speciesId:"azumarill",level:43,ivs:[4,15,13],shadow:false,fast:"BUBBLE",charged:["ICE_BEAM","HYDRO_PUMP"]}},
    {fixtureId:"EXP-GALVANTULA-SWAMPERT-4TURN-DEBUFF",shieldsA:1,shieldsB:1,a:{speciesId:"galvantula",level:25.5,ivs:[4,15,9],shadow:false,fast:"VOLT_SWITCH",charged:["LUNGE","DISCHARGE"]},b:{speciesId:"swampert",level:18.5,ivs:[6,15,12],shadow:false,fast:"MUD_SHOT",charged:["HYDRO_CANNON","EARTHQUAKE"]}},
    {fixtureId:"EXP-TALONFLAME-GALVANTULA-5TURN-BUFF",shieldsA:1,shieldsB:1,a:{speciesId:"talonflame",level:25.5,ivs:[4,12,15],shadow:false,fast:"INCINERATE",charged:["FLAME_CHARGE","FIRE_BLAST"]},b:{speciesId:"galvantula",level:25.5,ivs:[4,15,9],shadow:false,fast:"VOLT_SWITCH",charged:["LUNGE","DISCHARGE"]}}
  ];
  function requireFn(o,n,owner){if(!o||typeof o[n]!=="function")throw new Error(`PVPOKE_API_MISMATCH: ${owner}.${n} missing`)}
  function configure(x,index,battle,shields){
    const p=new Pokemon(x.speciesId,index,battle);
    for(const fn of ["setLevel","setIV","selectMove","setShields","initialize"])requireFn(p,fn,"Pokemon");
    p.setLevel(x.level,false); p.setIV("atk",x.ivs[0]);p.setIV("def",x.ivs[1]);p.setIV("hp",x.ivs[2]);
    if(x.shadow){requireFn(p,"setShadowType","Pokemon");p.setShadowType("shadow")}
    p.selectMove("fast",x.fast);p.selectMove("charged",x.charged[0],0);p.selectMove("charged",x.charged[1],1);p.setShields(shields);p.initialize(false);battle.setNewPokemon(p,index,false);return p;
  }
  function safe(v){if(v==null||typeof v==="string"||typeof v==="boolean")return v;if(typeof v==="number")return Number.isFinite(v)?v:null;if(Array.isArray(v))return v.map(safe);if(typeof v==="object"){const o={};for(const k of Object.keys(v).sort())o[k]=safe(v[k]);return o}return String(v)}
  function timeline(events){return events.map((e,i)=>({i,turn:Number(e.turn??0),time:Number(e.time??0),actor:Number(e.actor??-1),type:String(e.type??""),name:String(e.name??""),values:safe(Array.isArray(e.values)?e.values:[]),editable:e.editable==null?null:Boolean(e.editable)}))}
  async function hash(text){const d=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(text));return[...new Uint8Array(d)].map(b=>b.toString(16).padStart(2,"0")).join("")}
  const results=[];
  for(const c of cases){
    const battle=new Battle();for(const fn of ["setCP","setLevelCap","setDecisionMethod","setBuffChanceModifier","setNewPokemon","simulate","getBattleRatings","getWinner","getTurns","getTurnsToWin"])requireFn(battle,fn,"Battle");
    battle.setCP(1500);battle.setLevelCap(50);battle.setDecisionMethod("default");battle.setBuffChanceModifier(-1);
    const a=configure(c.a,0,battle,c.shieldsA),b=configure(c.b,1,battle,c.shieldsB),tl=timeline(battle.simulate()||[]),ratings=battle.getBattleRatings().map(Number),w=battle.getWinner();
    const winner=!w||w.pokemon===false||w.pokemon==null?null:Number(w.pokemon.index);
    results.push({fixtureId:c.fixtureId,shieldsA:c.shieldsA,shieldsB:c.shieldsB,battleRatingA:ratings[0],battleRatingB:ratings[1],winner,turns:Number(battle.getTurns()),turnsToWin:(battle.getTurnsToWin()||[]).map(Number),final:{a:{hp:Number(a.hp),energy:Number(a.energy),shields:Number(a.shields),attackStage:Number(a.statBuffs?.[0]??0),defenseStage:Number(a.statBuffs?.[1]??0)},b:{hp:Number(b.hp),energy:Number(b.energy),shields:Number(b.shields),attackStage:Number(b.statBuffs?.[0]??0),defenseStage:Number(b.statBuffs?.[1]??0)}},traceSha256:await hash(JSON.stringify(tl)),timeline:tl});
  }
  const payload={schemaVersion:EXPECTED.schemaVersion,generatedAt:new Date().toISOString(),source:{repository:"https://github.com/pvpoke/pvpoke",commitSha:proof.commitSha,siteVersion:proof.siteVersion,verifiedBy:proof.verifiedBy||"verify_pvpoke_checkout.py"},results};
  const text=JSON.stringify(payload,null,2);console.log("POKEMON_PVP_EXPANDED_EXPORT_START\n"+text+"\nPOKEMON_PVP_EXPANDED_EXPORT_END");try{await navigator.clipboard.writeText(text)}catch(_){}return payload;
})();
