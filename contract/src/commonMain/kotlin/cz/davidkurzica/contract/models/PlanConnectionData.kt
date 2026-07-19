package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionData(
    val planConnection: PlanConnection? = null
)
