package com.github.vevc;

import com.github.vevc.config.AppConfig;
import com.github.vevc.service.impl.Hysteria2ServiceImpl;
import com.github.vevc.util.ConfigUtil;
import com.github.vevc.util.LogUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Properties;

/**
 * @author vevc
 */
public final class WorldMagicPlugin extends JavaPlugin {

    private final Hysteria2ServiceImpl hysteria2Service = new Hysteria2ServiceImpl();

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.getLogger().info("WorldMagicPlugin enabled");
        LogUtil.init(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            // load config
            Properties props = ConfigUtil.loadConfiguration();
            AppConfig appConfig = AppConfig.load(props);
            if (Objects.isNull(appConfig)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    this.getLogger().info("Configuration not found, disabling plugin");
                    Bukkit.getPluginManager().disablePlugin(this);
                });
                return;
            }

            // install & start apps
            if (this.installApps(appConfig)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getScheduler().runTaskAsynchronously(this, hysteria2Service::startup);
                    Bukkit.getScheduler().runTaskAsynchronously(this, hysteria2Service::clean);
                });
            } else {
                Bukkit.getScheduler().runTask(this, () -> {
                    this.getLogger().info("Plugin install failed, disabling plugin");
                    Bukkit.getPluginManager().disablePlugin(this);
                });
            }
        });
    }

    private boolean installApps(AppConfig appConfig) {
        try {
            hysteria2Service.install(appConfig);
            return true;
        } catch (Exception e) {
            LogUtil.error("Plugin install failed", e);
            return false;
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.getLogger().info("WorldMagicPlugin disabled");
    }
}
