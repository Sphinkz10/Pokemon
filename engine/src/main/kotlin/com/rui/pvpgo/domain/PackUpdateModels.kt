package com.rui.pvpgo.domain

data class IntelligencePackBundle(
    val dataPack: VersionedGameDataPack,
    val metaPack: VersionedMetaPack? = null
)

enum class PackStageStatus { READY, REJECTED }

data class PackStageResult(
    val status: PackStageStatus,
    val errors: List<String>,
    val warnings: List<String> = emptyList()
) {
    init {
        if (status == PackStageStatus.READY) require(errors.isEmpty())
        if (status == PackStageStatus.REJECTED) require(errors.isNotEmpty())
    }
}

data class PackActivationState(
    val activeDataVersion: String?,
    val activeMetaVersion: String?,
    val previousDataVersion: String?,
    val previousMetaVersion: String?,
    val hasStagedBundle: Boolean
)
