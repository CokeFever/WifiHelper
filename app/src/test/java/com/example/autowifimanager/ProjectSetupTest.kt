package com.example.autowifimanager

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

/**
 * Smoke test to verify project setup, JUnit 5 integration,
 * and Kotest property testing module are correctly configured.
 */
class ProjectSetupTest : FunSpec({

    test("project compiles and Kotest runner works") {
        val result = 1 + 1
        result shouldBe 2
    }

    test("Kotest property testing module is available") {
        forAll(Arb.int(1..100)) { value ->
            value in 1..100
        }
    }
})
