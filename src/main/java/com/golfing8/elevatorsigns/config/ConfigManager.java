package com.golfing8.elevatorsigns.config;

import java.lang.reflect.Field;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;
import com.golfing8.elevatorsigns.Color;
import org.bukkit.Bukkit;
import com.golfing8.elevatorsigns.config.annotation.Configurable;
import java.io.InputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import com.golfing8.elevatorsigns.signhandler.SignHandler;

/**
 * Manages our config and loads the variables from it through our @Configurable annotation.
 */
public class ConfigManager
{
    private final SignHandler mainClass;
    private final Plugin plugin;
    private final FileConfiguration defConfig;
    private final FileConfiguration pluginConfig;
    private File pluginFile;

    public ConfigManager(SignHandler mainClass, Plugin plugin) {
        this.mainClass = mainClass;
        this.plugin = plugin;
        InputStream inputStream = this.getClass().getResourceAsStream("/config.yml");
        this.defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
        this.pluginFile = new File(plugin.getDataFolder(), "config.yml");
        this.pluginConfig = YamlConfiguration.loadConfiguration(this.pluginFile);
    }

    public void reloadConfig() {
        try {
            this.pluginConfig.load(this.pluginFile);
            boolean needSave = false;
            for (Field field : this.mainClass.getClass().getSuperclass().getDeclaredFields()) {
                Configurable configurable = null;
                field.setAccessible(true);
                if (field.isAnnotationPresent(Configurable.class)) {
                    try {
                        configurable = field.getAnnotation(Configurable.class);
                        String name = configurable.name().equals("") ? field.getName() : configurable.name();
                        String path = (configurable.path().endsWith(".") || configurable.path().length() == 0) ? configurable.path() : (configurable.path() + ".");
                        if (!this.pluginConfig.contains(path + name)) {
                            throw new IllegalArgumentException();
                        }
                        Object thing = this.pluginConfig.get(path + name);
                        field.set(this.mainClass, thing);
                    }
                    catch (NullPointerException | IllegalAccessException | IllegalArgumentException e2) {
                        if (configurable == null) {
                            continue;
                        }
                        String name2 = configurable.name().equals("") ? field.getName() : configurable.name();
                        String path2 = (configurable.path().endsWith(".") || configurable.path().length() == 0) ? configurable.path() : (configurable.path() + ".");
                        Object value = this.defConfig.get(path2 + name2);
                        this.pluginConfig.set(path2 + name2, value);
                        Bukkit.getConsoleSender().sendMessage(Color.c(String.format("[%s] - Error setting " + field.getName() + " due to a config error! (Setting default value: " + value + ")", this.plugin.getName())));
                        needSave = true;
                    }
                }
            }
            if (needSave) {
                this.plugin.saveConfig();
                this.pluginConfig.save(this.pluginFile);
                this.reloadConfig();
            }
        }
        catch (IOException | InvalidConfigurationException ex5) {
            this.plugin.saveDefaultConfig();
            this.pluginFile = new File(this.plugin.getDataFolder(), "config.yml");
            this.reloadConfig();
        }
    }
}
