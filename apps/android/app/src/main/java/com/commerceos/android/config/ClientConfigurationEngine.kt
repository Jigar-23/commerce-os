package com.commerceos.android.config

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Global Client Configuration Engine & Provider.
 * Serves as the single source of client behavior, identity, theme, terminology, feature flags,
 * and layout preferences for Commerce OS.
 */
object ClientConfigProvider {

    private val validator = ClientConfigValidator()
    private val tenantResolver = DefaultTenantResolver()
    private var persistenceManager: ConfigPersistenceManager? = null

    private val _currentConfig = MutableStateFlow(ClientConfiguration.DefaultGeneric)
    val currentConfig: StateFlow<ClientConfiguration> = _currentConfig.asStateFlow()

    private val _configState = MutableStateFlow<ClientConfigState>(ClientConfigState.Uninitialized)
    val configState: StateFlow<ClientConfigState> = _configState.asStateFlow()

    private var currentEnvironment: TenantEnvironment = TenantEnvironment.PRODUCTION
    private var isProductionLocked: Boolean = false

    fun initPersistence(context: Context) {
        persistenceManager = ConfigPersistenceManager(context)
    }

    fun setTenantEnvironment(env: TenantEnvironment) {
        currentEnvironment = env
    }

    fun activeConfig(): ClientConfiguration = _currentConfig.value

    /**
     * Client Bootstrap Engine executing configuration resolution before the first screen is presented.
     */
    fun bootstrap(
        environment: TenantEnvironment = currentEnvironment,
        context: Context? = null,
        tenantIdOverride: String? = null,
        onComplete: ((ClientConfiguration) -> Unit)? = null
    ) {
        setTenantEnvironment(environment)
        if (context != null) {
            initPersistence(context)
        }

        _configState.value = ClientConfigState.Loading

        val pm = persistenceManager ?: ConfigPersistenceManager(context)
        
        // 1. Resolve tenant config
        val resolvedConfig = tenantResolver.resolveClientConfig(environment, tenantIdOverride)

        // 2. Check persistence cache / last known good
        val cachedConfig = pm.getCachedConfig()
        val candidateConfig = cachedConfig ?: resolvedConfig

        // 3. Validate candidate configuration
        val validationResult = validator.validate(candidateConfig)

        if (validationResult.isValid) {
            val migratedConfig = pm.migrateIfNeeded(candidateConfig)
            _currentConfig.value = migratedConfig
            pm.saveLastKnownGood(migratedConfig)
            _configState.value = ClientConfigState.Success(
                config = migratedConfig,
                isFromCache = cachedConfig != null,
                isLastKnownGood = false
            )
            onComplete?.invoke(migratedConfig)
        } else {
            // Invalid config fallback to Last Known Good or Default Generic
            val lkgConfig = pm.getLastKnownGood() ?: ClientConfiguration.DefaultGeneric
            _currentConfig.value = lkgConfig
            _configState.value = ClientConfigState.Failure(
                errorMessage = "Invalid configuration: ${validationResult.errors.joinToString(", ")}",
                fallbackConfig = lkgConfig
            )
            onComplete?.invoke(lkgConfig)
        }
    }

    /**
     * Runtime switching between white-label client profiles with validation and security guards.
     */
    fun switchClientConfig(newConfig: ClientConfiguration) {
        if (isProductionLocked && currentEnvironment == TenantEnvironment.PRODUCTION) {
            throw SecurityException("Client configuration switching is locked in Production tenant mode.")
        }

        val validationResult = validator.validate(newConfig)
        if (!validationResult.isValid) {
            // Fallback to Last Known Good if invalid
            val pm = persistenceManager ?: ConfigPersistenceManager()
            val fallback = pm.getLastKnownGood() ?: _currentConfig.value
            _configState.value = ClientConfigState.Failure(
                errorMessage = "Validation failed: ${validationResult.errors.joinToString(", ")}",
                fallbackConfig = fallback
            )
            return
        }

        _currentConfig.value = newConfig
        _configState.value = ClientConfigState.Success(newConfig)
        persistenceManager?.saveLastKnownGood(newConfig)
    }

    fun lockProductionTenantSwitching(locked: Boolean) {
        isProductionLocked = locked
    }
}
