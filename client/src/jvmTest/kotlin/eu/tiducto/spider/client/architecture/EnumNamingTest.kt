package eu.tiducto.spider.client.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/** Enum constants are constants, so every enum in `:client` (public and generated) names them SCREAMING_SNAKE_CASE. */
class EnumNamingTest {

    @Test
    fun `enum constants are SCREAMING_SNAKE_CASE`() {
        Konsist.scopeFromProduction(moduleName = "client", sourceSetName = "commonMain")
            .classes()
            .filter { it.hasEnumModifier }
            .flatMap { it.enumConstants }
            .assertTrue { it.name.matches(Regex("[A-Z][A-Z0-9_]*")) }
    }
}
