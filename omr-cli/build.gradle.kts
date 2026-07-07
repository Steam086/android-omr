plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":omr-core"))
}

application {
    mainClass.set("com.answercard.grader.cli.OmrCliKt")
}
