package eu.tiducto.spider.client

internal object PersistedQueries {
    data class Op(val id: String, val path: String)

    val DEPARTURES = Op("5ca190e60b81d09b60da95ed3b92377ee1a73cdf5236383bb883a1b230cf6811", "departures")
    val PLAN = Op("70c90bd46b3c765f176dda39bbb2e714b785865d70e14f6cb6d9d72d7a77c210", "plan")
    val PLAN_STREAM = Op("1c7886ea99de8b6124b2363d2d935baf2b6c0a8e06144f52c596e43fccec9fb9", "plan-stream")
    val TRIP = Op("dc29ebef5bfcbe8c921e4381bfe0a4b9d869cd011fdade6c155f1a21bd3e5c33", "trip")
}
