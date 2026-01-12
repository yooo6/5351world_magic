package com.github.vevc.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

/**
 * Certificate utility using system keytool and openssl commands.
 * Compatible with Java 21 module system - does not use internal APIs.
 */
public class CertificateUtil {

    private static final String KEYSTORE_FILE = "hysteria.jks";
    private static final String DER_FILE = "hysteria.der";
    private static final String CERT_FILE = "hysteria.crt";
    private static final String P12_FILE = "hysteria.p12";
    private static final String KEY_FILE = "hysteria.key";

    private static final String KEYSTORE_PWD = "hysteria_pwd";

    public static void generateCertificates(File workDir) throws Exception {
        LogUtil.hysteria2Info("Starting certificate generation using keytool...");

        // Clean up old certificate files to ensure clean generation environment
        File keystoreFile = new File(workDir, KEYSTORE_FILE);
        File derFile = new File(workDir, DER_FILE);
        File certFile = new File(workDir, CERT_FILE);
        File p12File = new File(workDir, P12_FILE);
        File keyFile = new File(workDir, KEY_FILE);

        Files.deleteIfExists(keystoreFile.toPath());
        Files.deleteIfExists(derFile.toPath());
        Files.deleteIfExists(p12File.toPath());
        Files.deleteIfExists(certFile.toPath());
        Files.deleteIfExists(keyFile.toPath());

        LogUtil.hysteria2Info("Cleaned up old certificate files");

        // Check OpenSSL availability
        boolean opensslAvailable = isOpensslAvailable();
        if (!opensslAvailable) {
            LogUtil.hysteria2Info("OpenSSL not available, will use DER/PKCS12 format as fallback");
        }

        // Step 1: Generate RSA keypair and save to JKS keystore
        int step1 = executeCommand(
                "keytool", "-genkeypair",
                "-alias", "hysteria",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storepass", KEYSTORE_PWD,
                "-keypass", KEYSTORE_PWD,
                "-dname", "CN=localhost"
        );
        if (step1 != 0) throw new Exception("keytool -genkeypair failed with code: " + step1);
        LogUtil.hysteria2Info("Step 1 ✓: Generated RSA keypair");

        // Step 2: Export certificate from JKS to DER format
        int step2 = executeCommand(
                "keytool", "-export",
                "-alias", "hysteria",
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storepass", KEYSTORE_PWD,
                "-file", derFile.getAbsolutePath()
        );
        if (step2 != 0) throw new Exception("keytool -export failed with code: " + step2);
        LogUtil.hysteria2Info("Step 2 ✓: Exported DER certificate");

        // Step 3: Convert DER certificate to PEM format using openssl (optional)
        if (opensslAvailable) {
            int step3 = executeCommand(
                    "openssl", "x509",
                    "-inform", "DER",
                    "-in", derFile.getAbsolutePath(),
                    "-out", certFile.getAbsolutePath()
            );
            if (step3 != 0) throw new Exception("openssl x509 failed with code: " + step3);
            LogUtil.hysteria2Info("Step 3 ✓: Converted to PEM certificate");
        } else {
            LogUtil.hysteria2Info("Step 3 - (skipped): OpenSSL not available, keeping DER certificate");
        }

        // Step 4: Convert JKS to PKCS12 format (for key extraction)
        int step4 = executeCommand(
                "keytool", "-importkeystore",
                "-srckeystore", keystoreFile.getAbsolutePath(),
                "-srcstorepass", KEYSTORE_PWD,
                "-srcalias", "hysteria",
                "-destkeystore", p12File.getAbsolutePath(),
                "-deststoretype", "PKCS12",
                "-deststorepass", KEYSTORE_PWD
        );
        if (step4 != 0) throw new Exception("keytool -importkeystore failed with code: " + step4);
        LogUtil.hysteria2Info("Step 4 ✓: Converted to PKCS12 keystore");

        // Step 5: Extract private key from PKCS12 to PEM format using openssl (optional)
        if (opensslAvailable) {
            int step5 = executeCommand(
                    "openssl", "pkcs12",
                    "-in", p12File.getAbsolutePath(),
                    "-passin", "pass:" + KEYSTORE_PWD,
                    "-nodes",
                    "-nocerts",
                    "-out", keyFile.getAbsolutePath()
            );
            if (step5 != 0) throw new Exception("openssl pkcs12 failed with code: " + step5);
            LogUtil.hysteria2Info("Step 5 ✓: Extracted private key to PEM");
        } else {
            LogUtil.hysteria2Info("Step 5 - (skipped): OpenSSL not available, keeping PKCS12 keystore");
        }

        // Step 6: Clean up temporary files based on what was generated
        Files.deleteIfExists(keystoreFile.toPath());
        
        if (opensslAvailable) {
            // OpenSSL available: clean up intermediate files, keep PEM files
            Files.deleteIfExists(derFile.toPath());
            Files.deleteIfExists(p12File.toPath());
            LogUtil.hysteria2Info("Step 6 ✓: Cleaned temporary files (PEM format)");
        } else {
            // OpenSSL not available: clean up JKS, keep DER and PKCS12
            LogUtil.hysteria2Info("Step 6 ✓: Cleaned temporary files (DER/PKCS12 format)");
        }

        // Verify generated files
        if (opensslAvailable) {
            if (!Files.exists(certFile.toPath())) {
                throw new Exception("Certificate file not generated: " + certFile.getAbsolutePath());
            }
            if (!Files.exists(keyFile.toPath())) {
                throw new Exception("Key file not generated: " + keyFile.getAbsolutePath());
            }
            LogUtil.hysteria2Info("TLS certificates generated successfully using PEM format!");
            LogUtil.hysteria2Info("Certificate: " + certFile.getAbsolutePath());
            LogUtil.hysteria2Info("Private Key: " + keyFile.getAbsolutePath());
        } else {
            if (!Files.exists(derFile.toPath())) {
                throw new Exception("DER certificate file not generated: " + derFile.getAbsolutePath());
            }
            if (!Files.exists(p12File.toPath())) {
                throw new Exception("PKCS12 keystore not generated: " + p12File.getAbsolutePath());
            }
            LogUtil.hysteria2Info("TLS certificates generated successfully using DER/PKCS12 format!");
            LogUtil.hysteria2Info("DER Certificate: " + derFile.getAbsolutePath());
            LogUtil.hysteria2Info("PKCS12 Keystore: " + p12File.getAbsolutePath());
        }
    }

    private static int executeCommand(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (InputStream in = process.getInputStream();
             InputStreamReader reader = new InputStreamReader(in);
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                LogUtil.info("[CMD] " + line);
            }
        }

        return process.waitFor();
    }

    public static boolean isKeytoolAvailable() throws Exception {
        return executeCommand("keytool", "-version") == 0;
    }

    public static boolean isOpensslAvailable() {
        try {
            int exitCode = executeCommand("openssl", "version");
            return exitCode == 0;
        } catch (Exception e) {
            LogUtil.hysteria2Info("OpenSSL not available: " + e.getMessage());
            return false;
        }
    }
}
