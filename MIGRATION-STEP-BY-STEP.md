# Guide de Migration Spring Batch - Étape par Étape

**Date**: 18 décembre 2025  
**Branche**: `migration/spring-batch`  
**Point d'entrée**: law-common (module socle)

---

## 🚀 Phase 1: Préparation Initiale (30 min)

### Étape 1.1: Créer branche Git

```bash
cd /Volumes/FOLDER/dev/projects/io.law

# Sauvegarder état actuel
git add .
git commit -m "feat: Save current state before Spring Batch migration"

# Créer branche migration
git checkout -b migration/spring-batch

# Push branche
git push -u origin migration/spring-batch
```

---

### Étape 1.2: Ajouter Spring Boot Parent

**Fichier**: `pom.xml` (racine)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 🆕 AJOUT: Spring Boot Parent -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>bj.gouv.sgg</groupId>
    <artifactId>io.law</artifactId>
    <version>${revision}</version>
    <packaging>pom</packaging>
    
    <name>IO.Law Parent</name>
    <description>Application batch extraction lois/décrets - Spring Batch</description>

    <modules>
        <module>law-common</module>
        <module>law-fetch</module>
        <module>law-download</module>
        <module>law-to-json</module>
        <module>law-consolidate</module>
        <module>law-app</module>
    </modules>

    <properties>
        <revision>2.0.0-SNAPSHOT</revision>  <!-- 🆕 Version 2.0 -->
        
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <!-- 🆕 Spring Boot gérera les versions -->
        <spring-boot.version>3.2.0</spring-boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Modules internes -->
            <dependency>
                <groupId>bj.gouv.sgg</groupId>
                <artifactId>law-common</artifactId>
                <version>${revision}</version>
            </dependency>
            
            <!-- 🆕 Spring Batch -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-batch</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>
            
            <!-- 🆕 H2 pour tests -->
            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
                <version>2.2.224</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <!-- 🆕 Spring Boot Maven Plugin -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <createIndex>true</createIndex>  <!-- Accélère démarrage -->
                    <excludeDevtools>true</excludeDevtools>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Action**:
```bash
# Tester compilation
mvn clean compile

# Si erreurs de versions, ajuster
```

---

### Étape 1.3: Migrer law-common vers Spring

**Fichier**: `law-common/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>bj.gouv.sgg</groupId>
        <artifactId>io.law</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>law-common</artifactId>
    <name>Law Common</name>
    <description>Common module avec Spring Context et Spring Data JPA</description>

    <dependencies>
        <!-- 🆕 Spring Boot Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <!-- 🆕 Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

        <!-- MySQL Driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        
        <!-- 🆕 H2 pour tests -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

### Étape 1.4: Créer Configuration Spring

**Fichier**: `law-common/src/main/java/bj/gouv/sgg/config/CommonConfiguration.java`

```java
package bj.gouv.sgg.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "bj.gouv.sgg")
@EntityScan(basePackages = "bj.gouv.sgg.entity")
@EnableJpaRepositories(basePackages = "bj.gouv.sgg.repository")
@Slf4j
public class CommonConfiguration {
    
    public CommonConfiguration() {
        log.info("✅ Law Common Configuration chargée");
    }
}
```

---

### Étape 1.5: Migrer AppConfig vers @ConfigurationProperties

**Fichier**: `law-common/src/main/java/bj/gouv/sgg/config/AppConfig.java`

```java
package bj.gouv.sgg.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "law")
@Data
@Slf4j
public class AppConfig {
    
    private String storageBasePath = "./data";
    private Integer currentYear;
    
    // Ancienne méthode getInstance() pour compatibilité
    private static AppConfig instance;
    
    public AppConfig() {
        instance = this;
        if (currentYear == null) {
            currentYear = java.time.Year.now().getValue();
        }
        log.info("✅ AppConfig initialisé: storagePath={}, currentYear={}", 
                 storageBasePath, currentYear);
    }
    
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static synchronized AppConfig getInstance() {
        return instance;
    }
    
    public Path getStoragePath() {
        return Paths.get(storageBasePath);
    }
    
    public Path getPdfPath(String type) {
        return getStoragePath().resolve("pdfs").resolve(type);
    }
    
    public Path getOcrPath(String type) {
        return getStoragePath().resolve("ocr").resolve(type);
    }
    
    public Path getJsonPath(String type) {
        return getStoragePath().resolve("articles").resolve(type);
    }
}
```

---

### Étape 1.6: Créer application.yml

**Fichier**: `law-common/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: io.law
  
  # 🍓 Raspi: Optimisations démarrage
  main:
    lazy-initialization: true
    banner-mode: off
  
  jmx:
    enabled: false
  
  # DataSource MySQL
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/law_db?useSSL=false&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      minimum-idle: 1
      maximum-pool-size: 5
      initialization-fail-timeout: -1
  
  # JPA/Hibernate
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true

# Configuration application
law:
  storage:
    base-path: ${LAW_STORAGE_PATH:./data}
  current-year: ${CURRENT_YEAR:}

# Logging
logging:
  level:
    root: INFO
    bj.gouv.sgg: INFO
    org.springframework: WARN
    org.hibernate: WARN
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg%n"
```

---

### Étape 1.7: Créer application-test.yml

**Fichier**: `law-common/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true

law:
  storage:
    base-path: ${java.io.tmpdir}/law-test

logging:
  level:
    root: INFO
    bj.gouv.sgg: DEBUG
```

---

### Étape 1.8: Migrer Services vers @Service

**Fichier**: `law-common/src/main/java/bj/gouv/sgg/service/LawDocumentValidator.java`

```java
package bj.gouv.sgg.service;

import bj.gouv.sgg.config.AppConfig;
import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor  // 🆕 Injection automatique
@Slf4j
public class LawDocumentValidator {
    
    private final AppConfig config;
    
    // Ancienne méthode getInstance() pour compatibilité
    private static LawDocumentValidator instance;
    
    public LawDocumentValidator(AppConfig config) {
        this.config = config;
        instance = this;
    }
    
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static LawDocumentValidator getInstance() {
        return instance;
    }
    
    public boolean mustFetch(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();
        
        if (status == ProcessingStatus.PENDING) return true;
        if (status == ProcessingStatus.NOT_FOUND) return false;
        
        return false;
    }
    
    public boolean isFetched(LawDocumentEntity entity) {
        ProcessingStatus status = entity.getStatus();
        
        if (status == ProcessingStatus.FETCHED || 
            status == ProcessingStatus.DOWNLOADED ||
            status == ProcessingStatus.OCRED ||
            status == ProcessingStatus.EXTRACTED ||
            status == ProcessingStatus.CONSOLIDATED) {
            return true;
        }
        
        if (pdfExists(entity) || ocrExists(entity) || jsonExists(entity)) {
            return true;
        }
        
        return false;
    }
    
    // ... autres méthodes identiques
    
    public boolean pdfExists(LawDocumentEntity entity) {
        Path path = getPdfPath(entity);
        return path != null && Files.exists(path);
    }
    
    public Path getPdfPath(LawDocumentEntity entity) {
        if (entity.getPdfPath() != null) {
            return Path.of(entity.getPdfPath());
        }
        return config.getPdfPath(entity.getType())
            .resolve(entity.getDocumentId() + ".pdf");
    }
    
    // ... autres méthodes path helpers
}
```

---

### Étape 1.9: Migrer Repository vers Spring Data JPA

**Fichier**: `law-common/src/main/java/bj/gouv/sgg/repository/LawDocumentRepository.java`

```java
package bj.gouv.sgg.repository;

import bj.gouv.sgg.entity.LawDocumentEntity;
import bj.gouv.sgg.entity.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawDocumentRepository extends JpaRepository<LawDocumentEntity, Long> {
    
    Optional<LawDocumentEntity> findByDocumentId(String documentId);
    
    List<LawDocumentEntity> findByTypeAndYear(String type, int year);
    
    List<LawDocumentEntity> findByStatusAndType(ProcessingStatus status, String type);
    
    @Query("SELECT d FROM LawDocumentEntity d WHERE d.type = :type " +
           "AND d.year BETWEEN :minYear AND :maxYear " +
           "AND d.status IN (" +
           "    bj.gouv.sgg.entity.ProcessingStatus.FETCHED, " +
           "    bj.gouv.sgg.entity.ProcessingStatus.DOWNLOADED, " +
           "    bj.gouv.sgg.entity.ProcessingStatus.OCRED, " +
           "    bj.gouv.sgg.entity.ProcessingStatus.EXTRACTED, " +
           "    bj.gouv.sgg.entity.ProcessingStatus.CONSOLIDATED" +
           ")")
    List<LawDocumentEntity> findFetchedByTypeAndYearRange(
        @Param("type") String type,
        @Param("minYear") int minYear,
        @Param("maxYear") int maxYear
    );
    
    long countByTypeAndYear(String type, int year);
}
```

---

### Étape 1.10: Test de Compilation

```bash
cd /Volumes/FOLDER/dev/projects/io.law

# Compiler law-common
mvn clean compile -pl law-common

# Si succès, compiler tout
mvn clean compile

# Vérifier erreurs
```

**Résultats attendus**:
- ✅ `law-common` compile avec Spring
- ⚠️ Les autres modules peuvent avoir des erreurs (normal, à migrer ensuite)

---

## ✅ Checkpoint Phase 1

À ce stade, vous devez avoir:

- [x] Branche Git `migration/spring-batch` créée
- [x] Parent POM avec Spring Boot Parent
- [x] law-common migré vers Spring
- [x] Configuration Spring (`application.yml`)
- [x] Services avec `@Service` et injection
- [x] Repository Spring Data JPA
- [x] Compilation law-common réussie

**Commit**:
```bash
git add .
git commit -m "feat(law-common): Migrate to Spring Boot and Spring Data JPA

- Add Spring Boot parent to root POM
- Convert AppConfig to @ConfigurationProperties
- Convert LawDocumentValidator to @Service
- Convert repository to Spring Data JPA interface
- Add application.yml configuration
- Keep backward compatibility with getInstance() methods"

git push origin migration/spring-batch
```

---

## 🚀 Phase 2: Premier Job Spring Batch (2-3h)

### Continuer avec: `MIGRATION-STEP-BY-STEP-PHASE2.md`

**Prochaine étape**: Migrer law-fetch avec premier job Spring Batch (`fetchCurrentJob`)

---

**Commandes de démarrage** :
```bash
# Démarrer la migration
cd /Volumes/FOLDER/dev/projects/io.law
git checkout -b migration/spring-batch

# Suivre les étapes 1.2 à 1.10 ci-dessus

# Tester
mvn clean compile -pl law-common

# Commit
git add .
git commit -m "feat(law-common): Migrate to Spring Boot"
git push origin migration/spring-batch
```

**Questions/Blocages** ? Vérifier :
1. Java 17 installé : `java --version`
2. Maven 3.8+ : `mvn --version`
3. MySQL running : `docker ps | grep mysql`
4. Logs compilation : `mvn clean compile 2>&1 | grep ERROR`
