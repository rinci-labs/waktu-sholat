plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

/**
 * The city table ships as a generated Kotlin source file so the app parses no CSV at runtime:
 * the compiler turns it into a static array inside the dex, which keeps cold start and APK size
 * minimal. `data/cities.csv` is the source of truth and is committed alongside the output.
 */
val csvFile = rootProject.layout.projectDirectory.file("data/cities.csv")
val generatedDir = layout.buildDirectory.dir("generated/cities")

val generateCityTable by tasks.registering {
    val csv = csvFile
    val outDir = generatedDir
    inputs.file(csv).withPropertyName("citiesCsv")
    outputs.dir(outDir).withPropertyName("generatedSource")
    doLast {
        val file = csv.asFile
        check(file.exists()) { "Missing dataset: ${file.absolutePath}" }
        val rows = file.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(',') }
            .onEach { check(it.size == 5) { "Malformed row: $it" } }
            .map { CityRow(it[0], it[1], it[2].toDouble(), it[3].toDouble(), it[4].toInt()) }
        check(rows.size >= 400) { "Dataset too small: ${rows.size} rows" }
        check(rows.map { it.name to it.province }.toSet().size == rows.size) { "Dataset has duplicate names" }

        val target = outDir.get().asFile.resolve("dev/rafa/waktusholat/core")
        target.mkdirs()
        target.resolve("Cities.kt").writeText(buildString(rows.size * 64) {
            append("package dev.rafa.waktusholat.core\n\n")
            append("// Generated from data/cities.csv by the :core `generateCityTable` task. Do not edit.\n")
            append("internal object Cities {\n")
            append("    val ALL: Array<City> = arrayOf(\n")
            rows.forEach { row ->
                append("        City(\"")
                append(row.name.replace("\\", "\\\\").replace("\"", "\\\""))
                append("\", \"")
                append(row.province.replace("\\", "\\\\").replace("\"", "\\\""))
                append("\", ")
                append(row.lat.toString())
                append(", ")
                append(row.lng.toString())
                append(", ")
                append(row.tz.toString())
                append("),\n")
            }
            append("    )\n")
            append("}\n")
        })
        logger.lifecycle("Generated ${rows.size} cities into ${target.resolve("Cities.kt")}")
    }
}

private data class CityRow(val name: String, val province: String, val lat: Double, val lng: Double, val tz: Int)

kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedDir)

tasks.named("compileKotlin") { dependsOn(generateCityTable) }
tasks.named("compileTestKotlin") { dependsOn(generateCityTable) }
