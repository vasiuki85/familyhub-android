plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("../app/src/main/java")
            kotlin.include("com/familyhub/domain/utils/SyncJoinPolicy.kt")
            kotlin.include("com/familyhub/domain/model/MemberRole.kt")
        }
        test {
            kotlin.srcDir("../app/src/test/java")
            kotlin.include("com/familyhub/domain/utils/SyncJoinPolicyTest.kt")
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

