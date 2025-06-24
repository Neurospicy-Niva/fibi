package icu.neurospicy.fibi

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.BindMode.READ_WRITE
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Path

/**
 * Singleton class to manage shared test containers across all AI test classes.
 * This ensures containers are started once and shared, preventing the issue where
 * containers restart between test classes and change database credentials.
 */
@Testcontainers
object SharedTestContainers {
    
    private val network = Network.newNetwork()
    private var initialized = false
    private val initLock = Object()
    
    @Container
    val mongo: MongoDBContainer = MongoDBContainer("mongo:5")
        .withNetwork(network)
        .withNetworkAliases("mongodb")
        .withEnv(mapOf("MONGO_INITDB_DATABASE" to "fibi"))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("container/mongodb/init.js"),
            "/docker-entrypoint-initdb.d/init.js"
        )
        .withFileSystemBind("data/mongodb", "/data/db", READ_WRITE)
        .withStartupAttempts(3)
        .waitingFor(Wait.forLogMessage(".*init\\.js.*", 1))

    @Container
    val signalMock: GenericContainer<*> = GenericContainer("icu.neurospicy/mock-signal-cli:latest")
        .withNetwork(network)
        .withNetworkAliases("signal-cli-mock")
        .withExposedPorts(8080)
        .waitingFor(Wait.forLogMessage(".*Running on all addresses.*", 1))

    /**
     * Initialize containers if not already initialized.
     * This method is thread-safe and ensures containers are started only once.
     */
    fun initialize() {
        synchronized(initLock) {
            if (!initialized) {
                try {
                    // Clean up any existing data
                    cleanupDataDirectories()
                    
                    // Start containers
                    mongo.start()
                    signalMock.start()
                    
                    initialized = true
                    println("SharedTestContainers initialized successfully")
                    
                    // Add shutdown hook to clean up containers when JVM exits
                    Runtime.getRuntime().addShutdownHook(Thread {
                        try {
                            println("Shutting down shared test containers...")
                            signalMock.stop()
                            mongo.stop()
                            network.close()
                            println("Shared test containers shut down successfully")
                        } catch (e: Exception) {
                            println("Error during container cleanup: ${e.message}")
                        }
                    })
                } catch (e: Exception) {
                    println("Error initializing shared test containers: ${e.message}")
                    throw e
                }
            }
        }
    }
    
    private fun cleanupDataDirectories() {
        try {
            // Clean up MongoDB data
            val mongoDataDir = Path.of("data/mongodb").toFile()
            if (mongoDataDir.exists()) {
                deleteRecursively(mongoDataDir)
            }
            mongoDataDir.mkdirs()
        } catch (e: Exception) {
            println("Warning: Could not clean up data directories: ${e.message}")
        }
    }
    
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
    
    /**
     * Get the signal mock API URL for dynamic property configuration
     */
    fun getSignalMockApiUrl(): String {
        initialize() // Ensure containers are started
        return "http://localhost:${signalMock.getFirstMappedPort()}/api/v1"
    }
    
    /**
     * Get MongoDB connection properties for dynamic property configuration
     */
    fun getMongoConnectionProperties(): Map<String, String> {
        initialize() // Ensure containers are started
        return mapOf(
            "spring.data.mongodb.host" to mongo.host,
            "spring.data.mongodb.port" to mongo.getMappedPort(27017).toString(),
            "spring.data.mongodb.database" to "fibi",
            "spring.data.mongodb.username" to "fibiapp",
            "spring.data.mongodb.password" to "whydoesitalwaysRAINonme",
            "spring.data.mongodb.authentication-database" to "fibi"
        )
    }
    
    /**
     * Configure dynamic properties for Spring Boot tests
     */
    @DynamicPropertySource
    @JvmStatic
    fun configureProperties(registry: DynamicPropertyRegistry) {
        initialize() // Ensure containers are started
        
        // Configure MongoDB connection
        val mongoProps = getMongoConnectionProperties()
        mongoProps.forEach { (key, value) ->
            registry.add(key) { value }
        }
        
        // Configure signal-cli API URL
        registry.add("signal-cli.api-url") { getSignalMockApiUrl() }
    }
} 