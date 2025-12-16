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
 * Configuration de la connexion MySQL avec Hibernate (sans Spring).
 * 
 * Charge la configuration depuis :
 * 1. application.properties (si présent)
 * 2. Variables d'environnement
 * 3. Valeurs par défaut (Docker local)
 */
@Slf4j
public class DatabaseConfig {
    
    private static DatabaseConfig instance;
    private EntityManagerFactory entityManagerFactory;
    private DataSource dataSource;
    
    private DatabaseConfig() {
        initialize();
    }
    
    public static DatabaseConfig getInstance() {
        if (instance == null) {
            synchronized (DatabaseConfig.class) {
                if (instance == null) {
                    instance = new DatabaseConfig();
                }
            }
        }
        return instance;
    }
    
    private void initialize() {
        log.info("🔄 Initializing database connection...");
        
        Properties dbProps = loadProperties();
        
        // Configuration Hibernate (sans DataSource HikariCP externe)
        Map<String, Object> hibernateProps = new HashMap<>();
        hibernateProps.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        hibernateProps.put("jakarta.persistence.jdbc.url", dbProps.getProperty("db.url"));
        hibernateProps.put("jakarta.persistence.jdbc.user", dbProps.getProperty("db.username"));
        hibernateProps.put("jakarta.persistence.jdbc.password", dbProps.getProperty("db.password"));
        
        // Hibernate behavior
        hibernateProps.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        hibernateProps.put("hibernate.hbm2ddl.auto", dbProps.getProperty("hibernate.hbm2ddl.auto", "update"));
        hibernateProps.put("hibernate.show_sql", dbProps.getProperty("hibernate.show_sql", "false"));
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
            throw new RuntimeException("Database initialization failed", e);
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
        props.putIfAbsent("db.url", getEnv("DB_URL", "jdbc:mysql://localhost:3306/law_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"));
        props.putIfAbsent("db.username", getEnv("DB_USERNAME", "root"));
        props.putIfAbsent("db.password", getEnv("DB_PASSWORD", "root"));
        
        // HikariCP pool
        props.putIfAbsent("db.pool.max-size", getEnv("DB_POOL_MAX_SIZE", "10"));
        props.putIfAbsent("db.pool.min-idle", getEnv("DB_POOL_MIN_IDLE", "2"));
        props.putIfAbsent("db.pool.connection-timeout", getEnv("DB_POOL_CONNECTION_TIMEOUT", "30000"));
        
        // Hibernate
        props.putIfAbsent("hibernate.hbm2ddl.auto", getEnv("HIBERNATE_DDL_AUTO", "update"));
        props.putIfAbsent("hibernate.show_sql", getEnv("HIBERNATE_SHOW_SQL", "false"));
        
        log.info("🔧 Database URL: {}", props.getProperty("db.url"));
        log.info("🔧 Database User: {}", props.getProperty("db.username"));
        
        return props;
    }
    
    private DataSource createDataSource(Properties props) {
        HikariConfig config = new HikariConfig();
        
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.username"));
        config.setPassword(props.getProperty("db.password"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // Pool settings
        config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.max-size")));
        config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.min-idle")));
        config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.connection-timeout")));
        
        // Validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);
        
        // Pool name
        config.setPoolName("LawHikariPool");
        
        log.info("🏊 HikariCP pool configured: max={}, min={}", 
                 config.getMaximumPoolSize(), config.getMinimumIdle());
        
        return new HikariDataSource(config);
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
