import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.guava)
  api(project(":wisp:wisp-feature"))
  implementation(libs.kotlinStdLibJdk8)

  testFixturesApi(libs.jakartaInject)
  testFixturesApi(project(":wisp:wisp-feature"))
  testFixturesApi(project(":wisp:wisp-feature-testing"))
  testFixturesApi(project(":misk-feature"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.kotlinStdLibJdk8)
  testFixturesImplementation(libs.moshiCore)
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(project(":misk-testing-api"))

  testImplementation(libs.assertj)
  testImplementation(libs.guice)
  testImplementation(libs.jakartaInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.moshiCore)
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":wisp:wisp-moshi"))
  testImplementation(project(":misk-feature"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-testing"))

  testImplementation(libs.guice)
  testImplementation(libs.kotlinStdLibJdk8)
  testImplementation(libs.moshiCore)
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing-api"))

  testFixturesImplementation(libs.kotlinStdLibJdk8)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
