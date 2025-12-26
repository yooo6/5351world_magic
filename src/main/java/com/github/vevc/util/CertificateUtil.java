package com.github.vevc.util;

import sun.security.x509.AlgorithmId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

/**
 * Utility class for generating self-signed certificates in PEM format
 *
 * @author vevc
 */
public final class CertificateUtil {

    private static final String CERT_FILENAME = "hysteria.crt";
    private static final String KEY_FILENAME = "hysteria.key";
    private static final int KEY_SIZE = 2048;
    private static final int VALIDITY_DAYS = 365;

    /**
     * Generate self-signed certificate and private key files
     *
     * @param workDir the working directory to save certificate files
     * @return array containing [certPath, keyPath]
     * @throws Exception if certificate generation fails
     */
    public static String[] generateCertificates(File workDir) throws Exception {
        LogUtil.info("Generating self-signed certificate...");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        char[] keyPassword = "hysteria2".toCharArray();
        X500Principal issuer = new X500Principal("CN=localhost");
        Date from = new Date();
        Date to = new Date(from.getTime() + (long) VALIDITY_DAYS * 24 * 60 * 60 * 1000);

        X509Certificate cert = generateSelfSignedCertificate(keyPair, issuer, from, to);

        keyStore.setKeyEntry("hysteria", keyPair.getPrivate(), keyPassword, new Certificate[]{cert});

        File certFile = new File(workDir, CERT_FILENAME);
        File keyFile = new File(workDir, KEY_FILENAME);

        writeCertificateToPem(cert, certFile);
        writePrivateKeyToPem(keyPair.getPrivate(), keyFile);
        setFilePermissions(certFile.toPath());
        setFilePermissions(keyFile.toPath());

        LogUtil.info("Certificate generated successfully: " + certFile.getAbsolutePath());
        LogUtil.info("Private key saved: " + keyFile.getAbsolutePath());

        return new String[]{certFile.getAbsolutePath(), keyFile.getAbsolutePath()};
    }

    /**
     * Generate a self-signed X.509 certificate using Java's standard APIs
     * This method uses KeyCertGenerator to create the certificate
     */
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, X500Principal issuer,
                                                                  Date from, Date to) throws Exception {
        // Use reflection to access sun.security.x509 classes with proper module access
        try {
            Class<?> x509CertClass = Class.forName("sun.security.x509.X509CertImpl");
            Class<?> x509CertInfoClass = Class.forName("sun.security.x509.X509CertInfo");
            Class<?> certValidityClass = Class.forName("sun.security.x509.CertificateValidity");
            Class<?> certSerialNumberClass = Class.forName("sun.security.x509.CertificateSerialNumber");
            Class<?> x500NameClass = Class.forName("sun.security.x509.X500Name");
            Class<?> certificateX509KeyClass = Class.forName("sun.security.x509.CertificateX509Key");
            Class<?> certificateVersionClass = Class.forName("sun.security.x509.CertificateVersion");
            Class<?> algorithmIdClass = Class.forName("sun.security.x509.AlgorithmId");
            Class<?> certificateAlgorithmIdClass = Class.forName("sun.security.x509.CertificateAlgorithmId");

            Object certInfo = x509CertInfoClass.getDeclaredConstructor().newInstance();

            Object validity = certValidityClass.getDeclaredConstructor(Date.class, Date.class).newInstance(from, to);
            setField(x509CertInfoClass, certInfo, "validity", validity);

            Object serial = certSerialNumberClass.getDeclaredConstructor(BigInteger.class)
                    .newInstance(BigInteger.valueOf(System.currentTimeMillis()));
            setField(x509CertInfoClass, certInfo, "serialNumber", serial);

            Object owner = x500NameClass.getDeclaredConstructor(String.class).newInstance("CN=localhost");
            setField(x509CertInfoClass, certInfo, "subject", owner);
            setField(x509CertInfoClass, certInfo, "issuer", owner);

            Object certKey = certificateX509KeyClass.getDeclaredConstructor(java.security.PublicKey.class)
                    .newInstance(keyPair.getPublic());
            setField(x509CertInfoClass, certInfo, "key", certKey);

            Object version = certificateVersionClass.getDeclaredConstructor(int.class).newInstance(3);
            setField(x509CertInfoClass, certInfo, "version", version);

            Object sha256Oid = algorithmIdClass.getDeclaredConstructor(Object.class).newInstance(
                    AlgorithmId.get("SHA256withRSA"));
            Object algorithm = certificateAlgorithmIdClass.getDeclaredConstructor(algorithmIdClass).newInstance(sha256Oid);
            setField(x509CertInfoClass, certInfo, "algorithmID", algorithm);

            Object cert = x509CertClass.getDeclaredConstructor(x509CertInfoClass).newInstance(certInfo);
            x509CertClass.getMethod("sign", java.security.PrivateKey.class, String.class)
                    .invoke(cert, keyPair.getPrivate(), "SHA256withRSA");

            return (X509Certificate) cert;
        } catch (Exception e) {
            throw new CertificateException("Failed to generate certificate", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) throws Exception {
        var setMethod = clazz.getMethod("set", String.class, Object.class);
        setMethod.invoke(obj, fieldName, value);
    }

    private static void writeCertificateToPem(X509Certificate cert, File file) throws IOException, CertificateException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            fos.write(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(cert.getEncoded()).getBytes());
            fos.write("\n-----END CERTIFICATE-----\n".getBytes());
        }
    }

    private static void writePrivateKeyToPem(PrivateKey key, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("-----BEGIN PRIVATE KEY-----\n".getBytes());
            fos.write(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded()).getBytes());
            fos.write("\n-----END PRIVATE KEY-----\n".getBytes());
        }
    }

    private static void setFilePermissions(Path path) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(path, perms);
    }

    private CertificateUtil() {
        throw new IllegalStateException("Utility class");
    }
}
