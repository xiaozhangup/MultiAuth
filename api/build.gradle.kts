plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    compileOnly(files("../lib/velocity-3.4.0-SNAPSHOT-523.jar"))
}

tasks.jar {
    archiveBaseName.set("MultiAuth")
    archiveClassifier.set("Api")
}
