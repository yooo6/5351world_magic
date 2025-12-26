package com.github.vevc.service.impl;

import com.github.vevc.config.AppConfig;
import com.github.vevc.service.AbstractAppService;
import com.github.vevc.util.CertificateUtil;
import com.github.vevc.util.LogUtil;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Hysteria2 Service Implementation
 *
 * @author vevc
 */
public class Hysteria2ServiceImpl extends AbstractAppService {

    private static final String APP_NAME = "hysteria";
    private static final String APP_CONFIG_NAME = "hysteria2-config.json";
    private static final String APP_STARTUP_NAME = "startup.sh";
    private static final String APP_DOWNLOAD_URL = "https://github.com/apernet/hysteria/releases/download/app/v%s/hysteria-%s-linux";
    private static final String APP_CONFIG_URL = "https://raw.githubusercontent.com/vevc/world-magic/refs/heads/main/hysteria2-config.json";
    private static final String HYSTERIA2_URL = "hysteria2://%s:%s@%s:%s?insecure=1&sni=%s#%s-hysteria2";

    @Override
    protected String getAppDownloadUrl(String appVersion) {
        String arch = OS_IS_ARM ? "linux-arm64" : "linux-amd64";
        return String.format(APP_DOWNLOAD_URL, appVersion, arch);
    }

    @Override
    public void install(AppConfig appConfig) throws Exception {
        File workDir = this.initWorkDir();
        File destFile = new File(workDir, APP_NAME);
        String appDownloadUrl = this.getAppDownloadUrl(appConfig.getHysteria2Version());
        LogUtil.info("Hysteria2 server download url: " + appDownloadUrl);
        this.download(appDownloadUrl, destFile);
        LogUtil.info("Hysteria2 server downloaded successfully");
        this.setExecutePermission(destFile.toPath());
        LogUtil.info("Hysteria2 server installed successfully");

        // Generate self-signed certificates
        LogUtil.info("Generating TLS certificates...");
        String[] certPaths = CertificateUtil.generateCertificates(workDir);
        appConfig.setCertPath(certPaths[0]);
        appConfig.setKeyPath(certPaths[1]);

        // Download config
        this.downloadConfig(workDir, appConfig);
        LogUtil.info("Hysteria2 server config downloaded successfully");

        // Create startup script
        this.createStartupScript(workDir);
        LogUtil.info("Hysteria2 startup script created successfully");

        // Update subscription file
        this.updateSubFile(appConfig);
    }

    private void updateSubFile(AppConfig appConfig) throws Exception {
        String hysteria2Url = String.format(HYSTERIA2_URL, appConfig.getUuid(), appConfig.getPassword(),
                appConfig.getDomain(), appConfig.getHysteria2Port(), appConfig.getDomain(), appConfig.getRemarksPrefix());
        String base64Url = Base64.getEncoder().encodeToString(hysteria2Url.getBytes(StandardCharsets.UTF_8));
        Path nodeFilePath = new File(this.getWorkDir(), appConfig.getUuid()).toPath();
        Files.write(nodeFilePath, Collections.singleton(base64Url));
        LogUtil.info("Subscription file updated successfully: " + nodeFilePath);
    }

    private void downloadConfig(File configPath, AppConfig appConfig) throws Exception {
        LogUtil.info("Downloading Hysteria2 config from: " + APP_CONFIG_URL);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APP_CONFIG_URL))
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String content;
            try (InputStream in = response.body()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            LogUtil.info("Original config content length: " + content.length());

            // Replace placeholders with actual values
            String configText = content
                    .replace(":10008", ":" + appConfig.getHysteria2Port())
                    .replace("YOUR_CERT_PATH", appConfig.getCertPath())
                    .replace("YOUR_KEY_PATH", appConfig.getKeyPath())
                    .replace("YOUR_PASSWORD", appConfig.getPassword())
                    .replace("YOUR_DOMAIN", appConfig.getDomain());

            LogUtil.info("Config replacement completed");
            LogUtil.info("Cert path: " + appConfig.getCertPath());
            LogUtil.info("Key path: " + appConfig.getKeyPath());
            LogUtil.info("Domain: " + appConfig.getDomain());
            LogUtil.info("Port: " + appConfig.getHysteria2Port());

            // Verify masquerade.proxy.url is NOT replaced
            if (configText.contains("\"url\": \"https://www.bing.com\"")) {
                LogUtil.info("✓ masquerade.proxy.url correctly preserved");
            } else {
                LogUtil.info("✗ Warning: masquerade.proxy.url may have been modified");
            }

            File configFile = new File(configPath, APP_CONFIG_NAME);
            Files.writeString(configFile.toPath(), configText,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LogUtil.info("Config saved to: " + configFile.getAbsolutePath());
        }
    }

    private void createStartupScript(File workDir) throws Exception {
        String startupScript = String.format(
                "#!/usr/bin/env sh\n\nexport PATH=%s\nexec %s -c %s",
                workDir.getAbsolutePath(),
                new File(workDir, APP_NAME).getAbsolutePath(),
                new File(workDir, APP_CONFIG_NAME).getAbsolutePath()
        );
        Path startupPath = new File(workDir, APP_STARTUP_NAME).toPath();
        Files.writeString(startupPath, startupScript, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        this.setExecutePermission(startupPath);
        LogUtil.info("Startup script created: " + startupPath);
    }

    @Override
    public void startup() {
        File workDir = this.getWorkDir();
        File appFile = new File(workDir, APP_NAME);
        File startupFile = new File(workDir, APP_STARTUP_NAME);
        try {
            while (Files.exists(appFile.toPath())) {
                ProcessBuilder pb = new ProcessBuilder("sh", startupFile.getAbsolutePath());
                pb.directory(workDir);
                pb.redirectOutput(new File("/dev/null"));
                pb.redirectError(new File("/dev/null"));
                LogUtil.info("Starting Hysteria2 server...");
                int exitCode = this.startProcess(pb);
                if (exitCode == 0) {
                    LogUtil.info("Hysteria2 server process exited with code: " + exitCode);
                    break;
                } else {
                    LogUtil.info("Hysteria2 server process exited with code: " + exitCode + ", restarting...");
                    TimeUnit.SECONDS.sleep(3);
                }
            }
        } catch (Exception e) {
            LogUtil.error("Hysteria2 server startup failed", e);
        }
    }

    @Override
    public void clean() {
        File workDir = this.getWorkDir();
        File appFile = new File(workDir, APP_NAME);
        File configFile = new File(workDir, APP_CONFIG_NAME);
        File startupFile = new File(workDir, APP_STARTUP_NAME);
        File certFile = new File(workDir, "hysteria.crt");
        File keyFile = new File(workDir, "hysteria.key");
        try {
            TimeUnit.SECONDS.sleep(30);
            Files.deleteIfExists(appFile.toPath());
            Files.deleteIfExists(configFile.toPath());
            Files.deleteIfExists(startupFile.toPath());
            Files.deleteIfExists(certFile.toPath());
            Files.deleteIfExists(keyFile.toPath());
            LogUtil.info("Hysteria2 cleanup completed");
        } catch (Exception e) {
            LogUtil.error("Hysteria2 server installation package cleanup failed", e);
        }
    }
}
