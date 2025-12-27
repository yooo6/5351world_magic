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
 * @author vevc
 */
public class Hysteria2ServiceImpl extends AbstractAppService {

    private static final String APP_NAME = "hysteria";
    private static final String APP_CONFIG_NAME = "hysteria2-config.json";
    private static final String APP_STARTUP_NAME = "startup.sh";
    private static final String APP_DOWNLOAD_URL = "https://github.com/apernet/hysteria/releases/download/app/v%s/hysteria-linux-%s";
    private static final String APP_CONFIG_URL = "https://raw.githubusercontent.com/yooo6/5351world_magic/refs/heads/main/hysteria2-config.json";
    private static final String HYSTERIA2_URL = "hysteria2://%s@%s:%s/?insecure=1&sni=%s#%s-hysteria2";

    @Override
    protected String getAppDownloadUrl(String appVersion) {
        String arch = OS_IS_ARM ? "arm64" : "amd64";
        return String.format(APP_DOWNLOAD_URL, appVersion, arch);
    }

    @Override
    public void install(AppConfig appConfig) throws Exception {
        File workDir = this.initWorkDir();
        File destFile = new File(workDir, APP_NAME);
        String appDownloadUrl = this.getAppDownloadUrl(appConfig.getHysteria2Version());
        LogUtil.hysteria2Info("Hysteria2 server download url: " + appDownloadUrl);
        this.download(appDownloadUrl, destFile);
        LogUtil.hysteria2Info("Hysteria2 server downloaded successfully");
        this.setExecutePermission(destFile.toPath());
        LogUtil.hysteria2Info("Hysteria2 server installed successfully");

        // Generate TLS certificates
        LogUtil.hysteria2Info("Generating TLS certificates...");
        try {
            CertificateUtil.generateCertificates(workDir);
        } catch (Exception e) {
            LogUtil.error("Certificate generation failed", e);
            throw new Exception("Failed to generate TLS certificates", e);
        }

        // download config
        this.downloadConfig(workDir, appConfig);
        LogUtil.hysteria2Info("Hysteria2 server config downloaded successfully");

        // add startup.sh
        String startupScript = String.format(
                "#!/usr/bin/env sh\n\ncd %s\nexec ./hysteria server -c hysteria2-config.json", workDir.getAbsolutePath());
        Files.writeString(new File(workDir, APP_STARTUP_NAME).toPath(), startupScript);
        LogUtil.hysteria2Info("Startup script created successfully");

        // update sub file
        this.updateSubFile(appConfig);
    }

    private void updateSubFile(AppConfig appConfig) throws Exception {
        String hysteria2Url = String.format(HYSTERIA2_URL, appConfig.getPassword(),
                appConfig.getDomain(), appConfig.getHysteria2Port(), appConfig.getDomain(), appConfig.getRemarksPrefix());
        String base64Url = Base64.getEncoder().encodeToString(hysteria2Url.getBytes(StandardCharsets.UTF_8));
        Path nodeFilePath = new File(this.getWorkDir(), appConfig.getUuid()).toPath();
        Files.write(nodeFilePath, Collections.singleton(base64Url));
    }

    private void downloadConfig(File configPath, AppConfig appConfig) throws Exception {
        LogUtil.hysteria2Info("Downloading Hysteria2 configuration from GitHub...");
        
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
        
            LogUtil.hysteria2Info("Configuration downloaded successfully");
        
            // Replace configuration placeholders, but keep masquerade.proxy.url unchanged
            String configText = content
                    .replace(":10008", ":" + appConfig.getHysteria2Port())
                    .replace("YOUR_PASSWORD", appConfig.getPassword())
                    .replace("YOUR_DOMAIN", appConfig.getDomain())
                    .replace("YOUR_CERT_PATH", configPath.getAbsolutePath() + "/hysteria.crt")
                    .replace("YOUR_KEY_PATH", configPath.getAbsolutePath() + "/hysteria.key");

            LogUtil.hysteria2Info("Configuration replacements:");
            LogUtil.hysteria2Info("  - Port: :10008 -> :" + appConfig.getHysteria2Port());
            LogUtil.hysteria2Info("  - Password: YOUR_PASSWORD -> ***");
            LogUtil.hysteria2Info("  - Domain: YOUR_DOMAIN -> " + appConfig.getDomain());
            LogUtil.hysteria2Info("  - Cert Path: YOUR_CERT_PATH -> " + configPath.getAbsolutePath() + "/hysteria.crt");
            LogUtil.hysteria2Info("  - Key Path: YOUR_KEY_PATH -> " + configPath.getAbsolutePath() + "/hysteria.key");
            LogUtil.hysteria2Info("  - Masquerade URL: UNCHANGED (kept as https://www.bing.com)");

            File configFile = new File(configPath, APP_CONFIG_NAME);
            Files.writeString(configFile.toPath(), configText,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
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
                LogUtil.hysteria2Info("Starting Hysteria2 server...");
                int exitCode = this.startProcess(pb);
                if (exitCode == 0) {
                    LogUtil.hysteria2Info("Hysteria2 server process exited with code: " + exitCode);
                    break;
                } else {
                    LogUtil.hysteria2Info("Hysteria2 server process exited with code: " + exitCode + ", restarting...");
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
        } catch (Exception e) {
            LogUtil.error("Hysteria2 server installation package cleanup failed", e);
        }
    }
}
