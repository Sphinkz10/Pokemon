import com.rui.pvpgo.domain.*
import com.rui.pvpgo.engine.*

private fun checkThat(v:Boolean, m:String){ if(!v) error(m) }

private class FakeTransport : PvpokeSourceTransport {
    val revision = "0123456789abcdef0123456789abcdef01234567"
    val requested = mutableListOf<String>()
    override fun resolveMasterRevision(): String = revision
    override fun getPinnedText(revision: String, path: String): PvpokeFetchedText {
        checkThat(revision == this.revision, "All source files must use one pinned revision")
        requested += path
        return PvpokeFetchedText("payload:$path", 1_000L)
    }
}

private object FakeDecoder : PvpokePayloadDecoder {
    override fun decodeRankings(json: String) = listOf(
        PvpokeRankingRecord("alpha", listOf("FAST_FIRE","CHARGED_FIRE"), 95.0),
        PvpokeRankingRecord("beta", listOf("FAST_WATER","CHARGED_WATER"), 90.0)
    )
    override fun decodeFormats(json: String) = listOf(
        PvpokeFormatRecord("Great League", "all", 1500, true),
        PvpokeFormatRecord("Hidden", "hidden", 1500, false)
    )
}

private fun species(id:String,dex:Int,type:String)=PokemonSpecies(
    dex=dex,name=id,speciesId=id,types=listOf(type),fastMoveIds=listOf("FAST_${type.uppercase()}"),
    chargedMoveIds=listOf("CHARGED_${type.uppercase()}"),baseAttack=100,baseDefense=100,baseStamina=100
)
private fun moves()=listOf(
    PvpMove("FAST_FIRE","Fast Fire",type="fire",power=4,energyGain=8,turns=2),
    PvpMove("CHARGED_FIRE","Charged Fire",type="fire",power=55,energyCost=40),
    PvpMove("FAST_WATER","Fast Water",type="water",power=4,energyGain=8,turns=2),
    PvpMove("CHARGED_WATER","Charged Water",type="water",power=55,energyCost=40)
)

fun main(){
    val transport=FakeTransport()
    val provider=PvpokePublicMetaProvider(transport,FakeDecoder)
    val snapshot=provider.fetch(League.GREAT,"all")
    checkThat(snapshot.revision==transport.revision,"Revision mismatch")
    checkThat(transport.requested==listOf(PvpokePublicPaths.formatsPath,"src/data/rankings/all/overall/rankings-1500.json"),"Pinned paths mismatch: ${transport.requested}")
    checkThat(snapshot.recommendations.size==2,"Ranking decode mismatch")
    checkThat(snapshot.visibleFormats.size==1,"Only showCup formats should surface")
    checkThat(snapshot.recommendations.all { transport.revision in it.source },"Recommendation provenance missing pin")
    checkThat(snapshot.rankingsSha256 != snapshot.formatsSha256,"Independent content hashes expected")

    val catalog=listOf(species("alpha",1,"fire"),species("beta",2,"water"))
    val data=VersionedGameDataPackFactory.create(
        packId="gm",version="1",generatedAtEpochMs=1_000L,
        provenance=PackProvenance("PvPoke GameMaster",transport.revision),pokemon=catalog,moves=moves()
    )
    val rules=PvPRuleset(
        id="great-all",version="1",title="Great League",baseLeague=League.GREAT,
        rankingIvFloor=15,maxLevel=20.0,sourceName="fixture",sourceVersion="1",generatedAtEpochMs=1_000L
    )
    val meta=MetaPackAssembler.fromProviderSnapshot("meta","1",data,rules,snapshot,"season-fixture",topN=2)
    checkThat(meta.manifest.sourceVersion==transport.revision,"Meta Pack must carry provider revision")
    checkThat(MetaPackValidator.validate(meta,data).valid,"Provider-built Meta Pack must validate")

    val now=20L*24*60*60*1000
    val dataFresh=FreshnessEvaluator.evaluate(1_000L,now,now,transport.revision,transport.revision,FreshnessPolicy(30L*24*60*60*1000,60L*24*60*60*1000))
    checkThat(dataFresh.status==FreshnessStatus.FRESH && !dataFresh.updateAvailable,"Fresh state mismatch")
    val metaStale=FreshnessEvaluator.evaluate(0L,now,now,"oldrev","newrev",FreshnessPolicy(1L,2L))
    checkThat(metaStale.status==FreshnessStatus.STALE && metaStale.updateAvailable,"Stale/update state mismatch")
    val unknown=FreshnessEvaluator.evaluate(null,null,now,null,null,FreshnessPolicy(1L,2L))
    checkThat(unknown.status==FreshnessStatus.UNKNOWN,"Unknown state mismatch")

    println("PUBLIC_PROVIDER_F67_V1_29_OK revision=${snapshot.revision.take(12)} recommendations=${snapshot.recommendations.size} formats=${snapshot.visibleFormats.size}")
    println("DATA_FRESHNESS_F69_V1_29_OK states=${dataFresh.status}/${metaStale.status}/${unknown.status}")
}
