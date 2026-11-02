// OkHttp implementation of HttpTransport, used by ff-server-jvm and ff-android (OkHttp supports Android minSdk 21).
plugins {
    id("ff.jvm-library")
    id("ff.maven-publish")
}

dependencies {
    api(project(":ff-sync"))
    api(libs.okhttp)
}
