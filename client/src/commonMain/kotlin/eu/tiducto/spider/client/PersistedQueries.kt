package eu.tiducto.spider.client

internal object PersistedQueries {
    data class Op(val id: String, val path: String)

    val DEPARTURES = Op("e4ae3f49e06982173b38e945c3c02ef113be05e12564145faeade7758295f536", "departures")
    val PLAN = Op("679549e87f9653ff7a5a021c0b329a2c9658d4701c836139e63712dd9b77981f", "plan")
    val PLAN_STREAM = Op("40380fc4cf10397a4c20d039cc9428b73757c7fce0de2072ae1685a43efbfc15", "plan-stream")
    val TRIP = Op("4a10717f45697a241a0843902808cd696eb8ffd102e93238bf753f865d939a3f", "trip")
}
