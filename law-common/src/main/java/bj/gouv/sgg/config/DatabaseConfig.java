package bj.gouv.sgg.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration de la connexion MySQL avec Hibernate.
 * 
 * Charge la configuration depuis :
 * 1. application.properties (si présent)
 * 2. Variables d'environnement
 * 3. Valeurs par défaut (Docker local)
 */
@Slf4j
@SuppressWarnings("java:S6548") // Singleton pattern justifié pour EntityManagerFactory unique
public class DatabaseConfig {
    
    // Property keys
    private static final String PROP_DB_URL = "db.url";
    private static final String PROP_DB_USERNAME = "db.username";
    private static final String PROP_DB_PASSWORD = "db.password";
    private static final String PROP_HIBERNATE_DDL_AUTO = "hibernate.hbm2ddl.auto";
    private static final String PROP_HIBERNATE_SHOW_SQL = "hibernate.show_sql";
    
    private static DatabaseConfig instance;
    private EntityManagerFactory entityManagerFactory;
    private DataSource dataSource;
    
    private DatabaseConfig() {
        initialize();
    }
    
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    private void initialize() {
        log.info("🔄 Initializing database connection...");
        
        Properties dbProps = loadProperties();
        
        // Configuration Hibernate (sans DataSource HikariCP externe)
        Map<String, Object> hibernateProps = new HashMap<>();
        hibernateProps.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        hibernateProps.put("jakarta.persistence.jdbc.url", dbProps.getProperty(PROP_DB_URL));
        hibernateProps.put("jakarta.persistence.jdbc.user", dbProps.getProperty(PROP_DB_USERNAME));
        hibernateProps.put("jakarta.persistence.jdbc.password", dbProps.getProperty(PROP_DB_PASSWORD));
        
        // Hibernate behavior
        hibernateProps.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        hibernateProps.put(PROP_HIBERNATE_DDL_AUTO, dbProps.getProperty(PROP_HIBERNATE_DDL_AUTO, "update"));
        hibernateProps.put(PROP_HIBERNATE_SHOW_SQL, dbProps.getProperty(PROP_HIBERNATE_SHOW_SQL, "false"));
        hibernateProps.put("hibernate.format_sql", "true");
        hibernateProps.put("hibernate.use_sql_comments", "true");
        
        // Connection pool (Hibernate internal)
        hibernateProps.put("hibernate.connection.pool_size", "10");
        
        // Performance
        hibernateProps.put("hibernate.jdbc.batch_size", "50");
        hibernateProps.put("hibernate.order_inserts", "true");
        hibernateProps.put("hibernate.order_updates", "true");
        hibernateProps.put("hibernate.jdbc.batch_versioned_data", "true");
        
        try {
            this.entityManagerFactory = Persistence.createEntityManagerFactory(
                "law-persistence-unit",
                hibernateProps
            );
            log.info("✅ Database connection initialized successfully");
        } catch (Exception e) {
            log.error("❌ Failed to initialize database connection", e);
            throw new IllegalStateException("Database initialization failed", e);
        }
    }
    
    private Properties loadProperties() {
        Properties props = new Properties();
        
        // Essayer de charger application.properties
        Path propsPath = Paths.get("application.properties");
        if (Files.exists(propsPath)) {
            try (InputStream in = new FileInputStream(propsPath.toFile())) {
                props.load(in);
                log.info("📄 Loaded properties from application.properties");
            } catch (IOException e) {
                log.warn("⚠️ Failed to load application.properties: {}", e.getMessage());
            }
        }
        
        // Valeurs par défaut (Docker local)
        props.putIfAbsent(PROP_DB_URL, getEnv("DB_URL", "jdbc:mysql://localhost:3306/law_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"));
        props.putIfAbsent(PROP_DB_USERNAME, getEnv("DB_USERNAME", "root"));
        props.putIfAbsent(PROP_DB_PASSWORD, getEnv("DB_PASSWORD", "root"));
        
        // HikariCP pool
        props.putIfAbsent("db.pool.max-size", getEnv("DB_POOL_MAX_SIZE", "10"));
        props.putIfAbsent("db.pool.min-idle", getEnv("DB_POOL_MIN_IDLE", "2"));
        props.putIfAbsent("db.pool.connection-timeout", getEnv("DB_POOL_CONNECTION_TIMEOUT", "30000"));
        
        // Hibernate
        props.putIfAbsent(PROP_HIBERNATE_DDL_AUTO, getEnv("HIBERNATE_DDL_AUTO", "update"));
        props.putIfAbsent(PROP_HIBERNATE_SHOW_SQL, getEnv("HIBERNATE_SHOW_SQL", "false"));
        
        log.info("🔧 Database URL: {}", props.getProperty(PROP_DB_URL));
        log.info("🔧 Database User: {}", props.getProperty(PROP_DB_USERNAME));
        
        return props;
    }
    
    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Crée un nouvel EntityManager pour les opérations CRUD.
     * IMPORTANT : L'appelant doit fermer l'EntityManager après usage.
     */
    public EntityManager createEntityManager() {
        if (entityManagerFactory == null) {
            throw new IllegalStateException("EntityManagerFactory not initialized");
        }
        return entityManagerFactory.createEntityManager();
    }
    
    /**
     * Retourne l'EntityManagerFactory.
     */
    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }
    
    /**
     * Retourne la DataSource HikariCP.
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * Ferme les connexions (à appeler au shutdown de l'application).
     */
    public void shutdown() {
        log.info("🛑 Shutting down database connection...");
        
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        
        log.info("✅ Database connection closed");
    }
}
