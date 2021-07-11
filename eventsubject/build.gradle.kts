plugins {
    id("java")
    id("com.vanniktech.maven.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

mavenPublish {
    sonatypeHost = com.vanniktech.maven.publish.SonatypeHost.S01
}

dependencies {
    implementation("io.reactivex.rxjava3:rxjava:3.0.13")
    testImplementation("junit:junit:4.13")
}
