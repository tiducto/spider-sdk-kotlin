package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TransitPreferencesInput(
    val alight: AlightPreferencesInput? = null,
    val board: BoardPreferencesInput? = null,
    val filters: List<TransitFilterInput>? = null,
    val timetable: TimetablePreferencesInput? = null,
    val transfer: TransferPreferencesInput? = null
)
