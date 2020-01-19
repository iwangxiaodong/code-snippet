package pkg.example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/hello")
public class ExampleResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "hello";
    }
}


build.gradle

plugins {
    id 'java'
    id 'io.quarkus'
}

repositories {
     mavenLocal()
     mavenCentral()
}

dependencies {
    implementation enforcedPlatform('io.quarkus:quarkus-universe-bom:1.1.1.Final')
    implementation 'io.quarkus:quarkus-resteasy'
    //  可选 - implementation 'io.quarkus:quarkus-smallrye-jwt'
    /*  
        在META-INF/microprofile-config.properties中添加：
        mp.jwt.verify.publickey.location=https://example.com/.well-known/jwks.json
        mp.jwt.verify.issuer=https://example.com
    */
}

compileJava {
    options.compilerArgs << '-parameters'
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}


运行 ./gradle quarkusDev
访问网址和默认端口 - http://localhost:8080/
