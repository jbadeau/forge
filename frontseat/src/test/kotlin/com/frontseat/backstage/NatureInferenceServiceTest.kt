package com.frontseat.backstage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NatureInferenceServiceTest {
    
    private val service = NatureInferenceService()
    
    @Test
    fun `should infer Maven nature from pom xml`(@TempDir tempDir: Path) {
        // Create a pom.xml file
        val pomFile = tempDir.resolve("pom.xml")
        Files.write(pomFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
            </project>
        """.trimIndent().toByteArray())
        
        val natures = service.inferNatures(tempDir)
        
        assertTrue(natures.contains(ProjectNature.MAVEN))
    }
    
    @Test
    fun `should infer Spring Boot nature for service with SpringBootApplication`(@TempDir tempDir: Path) {
        // Create a pom.xml with Spring Boot
        val pomFile = tempDir.resolve("pom.xml")
        Files.write(pomFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.example</groupId>
                <artifactId>test-service</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter</artifactId>
                    </dependency>
                </dependencies>
            </project>
        """.trimIndent().toByteArray())
        
        // Create source directory structure
        val srcDir = tempDir.resolve("src/main/java/com/example")
        Files.createDirectories(srcDir)
        
        // Create a class with @SpringBootApplication
        val mainClass = srcDir.resolve("Application.java")
        Files.write(mainClass, """
            package com.example;
            
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            
            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
        """.trimIndent().toByteArray())
        
        val natures = service.inferNatures(tempDir, "service")
        
        assertTrue(natures.contains(ProjectNature.MAVEN))
        assertTrue(natures.contains(ProjectNature.SPRING_BOOT))
    }
    
    @Test
    fun `should infer NPM nature from package json`(@TempDir tempDir: Path) {
        val packageFile = tempDir.resolve("package.json")
        Files.write(packageFile, """
            {
              "name": "test-project",
              "version": "1.0.0",
              "dependencies": {
                "express": "^4.18.0"
              }
            }
        """.trimIndent().toByteArray())
        
        val natures = service.inferNatures(tempDir)
        
        assertTrue(natures.contains(ProjectNature.NPM))
        assertTrue(natures.contains(ProjectNature.NODEJS))
    }
    
    @Test
    fun `should infer React nature from package json`(@TempDir tempDir: Path) {
        val packageFile = tempDir.resolve("package.json")
        Files.write(packageFile, """
            {
              "name": "test-app",
              "version": "1.0.0",
              "dependencies": {
                "react": "^18.0.0",
                "react-dom": "^18.0.0"
              }
            }
        """.trimIndent().toByteArray())
        
        val natures = service.inferNatures(tempDir)
        
        assertTrue(natures.contains(ProjectNature.NPM))
        assertTrue(natures.contains(ProjectNature.REACT))
    }
    
    @Test
    fun `should apply manual overrides`() {
        val inferredNatures = setOf(ProjectNature.MAVEN, ProjectNature.SPRING_BOOT)
        val annotations = mapOf("frontseat.io/natures" to "MAVEN,DOCKER")
        
        val result = service.applyManualOverrides(inferredNatures, annotations)
        
        assertEquals(setOf(ProjectNature.MAVEN, ProjectNature.DOCKER), result)
    }
    
    @Test
    fun `should use inferred natures when no override present`() {
        val inferredNatures = setOf(ProjectNature.MAVEN, ProjectNature.SPRING_BOOT)
        val annotations = emptyMap<String, String>()
        
        val result = service.applyManualOverrides(inferredNatures, annotations)
        
        assertEquals(inferredNatures, result)
    }
    
    @Test
    fun `should infer Docker nature from Dockerfile`(@TempDir tempDir: Path) {
        val dockerfile = tempDir.resolve("Dockerfile")
        Files.write(dockerfile, """
            FROM openjdk:17-jre-slim
            COPY target/app.jar app.jar
            ENTRYPOINT ["java", "-jar", "app.jar"]
        """.trimIndent().toByteArray())
        
        val natures = service.inferNatures(tempDir)
        
        assertTrue(natures.contains(ProjectNature.DOCKER))
    }
}