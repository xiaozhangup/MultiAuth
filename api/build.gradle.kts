plugins {
    java
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("MultiAuth")
    archiveClassifier.set("Api")
}
