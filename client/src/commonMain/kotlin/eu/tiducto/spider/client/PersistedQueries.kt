package eu.tiducto.spider.client

internal object PersistedQueries {
    data class Op(val id: String, val path: String)

    val DEPARTURES = Op("70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0", "departures")
    val PLAN = Op("4ce89d3209a478dd7a75d2abffd9956e79e081bfbaeeeae33fb255309c59aa80", "plan")
    val TRIP = Op("e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920", "trip")
}
