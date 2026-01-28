package com.talexck.gameVoting.utils.language;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manager for multi-language support.
 * Loads and provides translations from language files.
 */
public class LanguageManager {
    private static LanguageManager instance;
    
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, FileConfiguration> languages;
    private String currentLanguage;
    private FileConfiguration currentConfig;

    private static final String[] SUPPORTED_LANGUAGES = {"en-US", "zh-CN", "en-UK"};
    private static final String DEFAULT_LANGUAGE = "en-US";

    private LanguageManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.languages = new HashMap<>();
        this.currentLanguage = DEFAULT_LANGUAGE;
    }

    /**
     * Initialize the language manager.
     *
     * @param plugin Plugin instance
     * @return LanguageManager instance
     */
    public static LanguageManager initialize(Plugin plugin) {
        if (instance == null) {
            instance = new LanguageManager(plugin);
            instance.loadLanguages();
        }
        return instance;
    }

    /**
     * Get the LanguageManager instance.
     *
     * @return LanguageManager instance
     */
    public static LanguageManager getInstance() {
        return instance;
    }

    /**
     * Load all language files.
     */
    private void loadLanguages() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        
        // Create lang folder if it doesn't exist
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Copy default language files from resources
        for (String lang : SUPPORTED_LANGUAGES) {
            File langFile = new File(langFolder, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath());
                        logger.info("Created default language file: " + lang + ".yml");
                    }
                } catch (IOException e) {
                    logger.severe("Failed to create language file: " + lang + ".yml - " + e.getMessage());
                }
            }

            // Load language file
            if (langFile.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                languages.put(lang, config);
                logger.info("Loaded language: " + lang);
            }
        }

        // Load configured language from config.yml
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
            ((org.bukkit.plugin.java.JavaPlugin) plugin).reloadConfig();
        }
        String configuredLang = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        logger.info("Loading language from config: " + configuredLang);
        setLanguage(configuredLang);
    }

    /**
     * Set the current language.
     *
     * @param language Language code (e.g., "en-US", "zh-CN")
     * @return true if language was set successfully
     */
    public boolean setLanguage(String language) {
        if (languages.containsKey(language)) {
            this.currentLanguage = language;
            this.currentConfig = languages.get(language);
            logger.info("Language set to: " + language);
            return true;
        }
        
        // Try case-insensitive match
        for (String key : languages.keySet()) {
            if (key.equalsIgnoreCase(language)) {
                this.currentLanguage = key;
                this.currentConfig = languages.get(key);
                logger.info("Language set to: " + key + " (matched from " + language + ")");
                return true;
            }
        }
        
        logger.warning("Language not found: " + language + ", using default: " + DEFAULT_LANGUAGE);
        this.currentLanguage = DEFAULT_LANGUAGE;
        this.currentConfig = languages.get(DEFAULT_LANGUAGE);
        return false;
    }

    /**
     * Get a translated message by key.
     *
     * @param key Message key (e.g., "voting.not_active")
     * @return Translated message, or key if not found
     */
    public String getMessage(String key) {
        if (currentConfig == null) {
            return key;
        }

        String message = currentConfig.getString(key);
        if (message == null) {
            // Fallback to default language
            FileConfiguration defaultConfig = languages.get(DEFAULT_LANGUAGE);
            if (defaultConfig != null) {
                message = defaultConfig.getString(key);
            }
        }

        return message != null ? message : key;
    }

    /**
     * Get a translated message with placeholder replacements.
     *
     * @param key Message key
     * @param placeholders Map of placeholder keys to values (e.g., "player" -> "Steve")
     * @return Translated message with placeholders replaced
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }

    /**
     * Get a translated message with single placeholder.
     *
     * @param key Message key
     * @param placeholderKey Placeholder key (without braces)
     * @param placeholderValue Placeholder value
     * @return Translated message with placeholder replaced
     */
    public String getMessage(String key, String placeholderKey, String placeholderValue) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholderKey, placeholderValue);
        return getMessage(key, placeholders);
    }

    /**
     * Get the current language code.
     *
     * @return Current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Get all supported language codes.
     *
     * @return Array of supported language codes
     */
    public String[] getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Reload all language files.
     */
    public void reload() {
        languages.clear();
        loadLanguages();
    }
}
