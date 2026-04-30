package app.ixo.wifihelper

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.IsolationMode
import io.kotest.property.PropertyTesting

/**
 * Kotest project-level configuration.
 * Sets default property testing iterations and isolation mode.
 */
class KotestProjectConfig : AbstractProjectConfig() {

    override val isolationMode = IsolationMode.InstancePerLeaf

    init {
        // Default minimum iterations for property-based tests
        PropertyTesting.defaultIterationCount = 100
    }
}
