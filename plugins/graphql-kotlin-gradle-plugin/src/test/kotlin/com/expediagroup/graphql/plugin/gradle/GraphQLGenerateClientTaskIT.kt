/*
 * Copyright 2020 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.expediagroup.graphql.plugin.gradle

import com.expediagroup.graphql.plugin.gradle.tasks.GENERATE_CLIENT_TASK_NAME
import com.expediagroup.graphql.plugin.gradle.tasks.GENERATE_TEST_CLIENT_TASK_NAME
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQLGenerateClientTaskIT : GraphQLGradlePluginAbstractIT() {

    @Test
    @Tag("kts")
    fun `verify generateClient task with defaults (kts)`(@TempDir tempDir: Path) {
        val testProjectDirectory = tempDir.toFile()
        /*
        project setup
        ----
        build.gradle.kts
        schema.graphql
        src
        |- main
          |- kotlin
            |- com.example.Application.kt
          |- resources
            |- JUnitQuery.graphql
         */
        val buildFileContents =
            """
            application {
              applicationDefaultJvmArgs = listOf("-DgraphQLEndpoint=${wireMockServer.baseUrl()}/graphql")
              mainClassName = "com.example.ApplicationKt"
            }

            val graphqlGenerateClient by tasks.getting(GraphQLGenerateClientTask::class) {
              packageName.set("com.example.generated")
              schemaFileName.set("${'$'}{project.projectDir}/schema.graphql")
            }
            """.trimIndent()
        testProjectDirectory.generateBuildFile(buildFileContents)
        testProjectDirectory.createTestFile("schema.graphql")
            .writeText(testSchema)
        testProjectDirectory.createTestFile("JUnitQuery.graphql", "src/main/resources")
            .writeText(testQuery)
        testProjectDirectory.createTestFile("Application.kt", "src/main/kotlin/com/example")
            .writeText(loadTemplate("Application", mapOf("customScalarsEnabled" to false)))
        // end project setup

        verifyGenerateClientTaskSuccess(testProjectDirectory)
        assertTrue(File(testProjectDirectory, "build/generated/source/graphql/main/com/example/generated/GraphQLTypeAliases.kt").exists())
    }

    @Test
    @Tag("kts")
    fun `generateClient task should fail on deprecated queries (kts)`(@TempDir tempDir: Path) {
        val testProjectDirectory = tempDir.toFile()
        val buildFileContents =
            """
            val graphqlGenerateClient by tasks.getting(GraphQLGenerateClientTask::class) {
              packageName.set("com.example.generated")
              schemaFileName.set("${'$'}{project.projectDir}/schema.graphql")
            }
            """.trimIndent()
        testProjectDirectory.generateBuildFile(buildFileContents)
        testProjectDirectory.createTestFile("schema.graphql")
            .writeText(testSchema)
        testProjectDirectory.createTestFile("DeprecatedQuery.graphql", "src/main/resources/queries")
            .writeText(loadResource("mocks/DeprecatedQuery.graphql"))
        // end project setup

        val buildResult = GradleRunner.create()
            .withProjectDir(testProjectDirectory)
            .withPluginClasspath()
            .withArguments(GENERATE_CLIENT_TASK_NAME)
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, buildResult.task(":$GENERATE_CLIENT_TASK_NAME")?.outcome)
        assertFalse(File(testProjectDirectory, "build/generated/source/graphql/main/com/example/generated/JUnitQuery.kt").exists())
    }

    @Test
    @Tag("kts")
    fun `verify generateClient task (kts)`(@TempDir tempDir: Path) {
        val testProjectDirectory = tempDir.toFile()
        /*
        project setup
        ----
        build.gradle.kts
        src
        |- main
          |- kotlin
            |- com.example.Application.kt
            |- com.example.UUIDScalarConverter.kt
          |- resources
            |- JUnitQuery.graphql
            |- DeprecatedQuery.graphql
         */
        val buildFileContents =
            """
            application {
              applicationDefaultJvmArgs = listOf("-DgraphQLEndpoint=${wireMockServer.baseUrl()}/graphql")
              mainClassName = "com.example.ApplicationKt"
            }

            val graphqlGenerateClient by tasks.getting(GraphQLGenerateClientTask::class) {
              packageName.set("com.example.generated")
              schemaFileName.set("${'$'}{project.projectDir}/schema.graphql")
              // optional config
              converters.put("UUID", ScalarConverterMapping("java.util.UUID", "com.example.UUIDScalarConverter"))
              allowDeprecatedFields.set(true)
              queryFiles.from(
                "${'$'}{project.projectDir}/src/main/resources/queries/JUnitQuery.graphql",
                "${'$'}{project.projectDir}/src/main/resources/queries/DeprecatedQuery.graphql"
              )
            }
            """.trimIndent()
        testProjectDirectory.generateBuildFile(buildFileContents)
        testProjectDirectory.createTestFile("schema.graphql")
            .writeText(testSchema)
        testProjectDirectory.createTestFile("JUnitQuery.graphql", "src/main/resources/queries")
            .writeText(testQuery)
        testProjectDirectory.createTestFile("DeprecatedQuery.graphql", "src/main/resources/queries")
            .writeText(loadResource("mocks/DeprecatedQuery.graphql"))
        testProjectDirectory.createTestFile("Application.kt", "src/main/kotlin/com/example")
            .writeText(loadTemplate("Application", mapOf("customScalarsEnabled" to true)))
        testProjectDirectory.createTestFile("UUIDScalarConverter.kt", "src/main/kotlin/com/example")
            .writeText(loadResource("mocks/UUIDScalarConverter.kt"))
        // end project setup

        verifyGenerateClientTaskSuccess(testProjectDirectory)
        assertTrue(File(testProjectDirectory, "build/generated/source/graphql/main/com/example/generated/DeprecatedQuery.kt").exists())
    }

    @Test
    @Tag("kts")
    fun `verify generateTestClient task (kts)`(@TempDir tempDir: Path) {
        val testProjectDirectory = tempDir.toFile()

        /*
        project setup
        ----
        build.gradle.kts
        schema.graphql
        src
        |- test
          |- kotlin
            |- com.example.GenerateClientTest.kt
          |- resources
            |- JUnitQuery.graphql
         */
        val buildFileContents =
            """
            val graphqlGenerateTestClient by tasks.getting(GraphQLGenerateClientTask::class) {
              packageName.set("com.example.generated")
              schemaFileName.set("${'$'}{project.projectDir}/schema.graphql")
            }

            tasks {
              test {
                systemProperty("graphQLEndpoint", "${wireMockServer.baseUrl()}/graphql")
              }
            }
            """.trimIndent()
        testProjectDirectory.generateBuildFile(buildFileContents)
        testProjectDirectory.createTestFile("schema.graphql")
            .writeText(testSchema)
        testProjectDirectory.createTestFile("JUnitQuery.graphql", "src/test/resources")
            .writeText(testQuery)
        testProjectDirectory.createTestFile("GenerateClientTest.kt", "src/test/kotlin/com/example")
            .writeText(loadTemplate("JUnit", mapOf("customScalarsEnabled" to false)))
        // end project setup

        val buildResult = GradleRunner.create()
            .withProjectDir(testProjectDirectory)
            .withPluginClasspath()
            .withArguments(GENERATE_TEST_CLIENT_TASK_NAME, "test")
            .build()

        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":$GENERATE_TEST_CLIENT_TASK_NAME")?.outcome)
        assertTrue(File(testProjectDirectory, "build/generated/source/graphql/test/com/example/generated/JUnitQuery.kt").exists())
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":test")?.outcome)
    }

    @Test
    @Tag("groovy")
    fun `verify generateClient task (groovy)`(@TempDir tempDir: Path) {
        val testProjectDirectory = tempDir.toFile()
        val buildFileContents =
            """
            application {
              applicationDefaultJvmArgs = ["-DgraphQLEndpoint=${wireMockServer.baseUrl()}/graphql"]
              mainClassName = "com.example.ApplicationKt"
            }

            graphqlGenerateClient {
              packageName = "com.example.generated"
              schemaFileName = "${'$'}{project.projectDir}/schema.graphql"
              // optional config
              allowDeprecatedFields = true
              converters["UUID"] = new com.expediagroup.graphql.plugin.generator.ScalarConverterMapping("java.util.UUID", "com.example.UUIDScalarConverter")
              queryFiles.from("${'$'}{project.projectDir}/src/main/resources/queries/JUnitQuery.graphql",
                "${'$'}{project.projectDir}/src/main/resources/queries/DeprecatedQuery.graphql")
            }
            """.trimIndent()
        testProjectDirectory.generateGroovyBuildFile(buildFileContents)

        testProjectDirectory.createTestFile("schema.graphql")
            .writeText(testSchema)
        testProjectDirectory.createTestFile("JUnitQuery.graphql", "src/main/resources/queries")
            .writeText(testQuery)
        testProjectDirectory.createTestFile("DeprecatedQuery.graphql", "src/main/resources/queries")
            .writeText(loadResource("mocks/DeprecatedQuery.graphql"))
        testProjectDirectory.createTestFile("Application.kt", "src/main/kotlin/com/example")
            .writeText(loadTemplate("Application", mapOf("customScalarsEnabled" to true)))
        testProjectDirectory.createTestFile("UUIDScalarConverter.kt", "src/main/kotlin/com/example")
            .writeText(loadResource("mocks/UUIDScalarConverter.kt"))
        // end project setup

        verifyGenerateClientTaskSuccess(testProjectDirectory)
        assertTrue(File(testProjectDirectory, "build/generated/source/graphql/main/com/example/generated/DeprecatedQuery.kt").exists())
    }

    private fun verifyGenerateClientTaskSuccess(testProjectDirectory: File) {
        val buildResult = GradleRunner.create()
            .withProjectDir(testProjectDirectory)
            .withPluginClasspath()
            .withArguments(GENERATE_CLIENT_TASK_NAME, "run")
            .build()
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":$GENERATE_CLIENT_TASK_NAME")?.outcome)
        assertTrue(File(testProjectDirectory, "build/generated/source/graphql/main/com/example/generated/JUnitQuery.kt").exists())
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":run")?.outcome)
    }
}
