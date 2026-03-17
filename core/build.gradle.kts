plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.tcoded.com/releases")
    maven("https://jitpack.io")
    maven("https://maven.elytrium.net/repo/")
}

dependencies {
    implementation(project(":api"))

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly(files("../lib/velocity-3.5.0-SNAPSHOT-580.jar"))
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("mysql:mysql-connector-java:8.0.23")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    compileOnly("com.github.NEZNAMY:TAB-API:5.5.0")
}

tasks.shadowJar {
    archiveBaseName.set("MultiAuth")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "cn.jason31416.multiauth.lib.hikari")
    exclude("org/slf4j/**")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
