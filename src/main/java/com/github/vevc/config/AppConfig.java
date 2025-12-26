package com.github.vevc.config;

import com.github.vevc.constant.AppConst;

import java.util.Properties;

/**
 * @author vevc
 */
public class AppConfig {

    private String domain;
    private String port;
    private String uuid;
    private String password;
    private String tuicVersion;
    private String hysteria2Version;
    private String hysteria2Port;
    private String certPath;
    private String keyPath;
    private String remarksPrefix;

    public static AppConfig load(Properties props) {
        if (props == null) {
            return null;
        }
        AppConfig cfg = new AppConfig();
        cfg.setDomain(props.getProperty(AppConst.DOMAIN));
        cfg.setPort(props.getProperty(AppConst.PORT));
        cfg.setUuid(props.getProperty(AppConst.UUID));
        cfg.setPassword(props.getProperty(AppConst.PASSWORD));
        cfg.setTuicVersion(props.getProperty(AppConst.TUIC_VERSION));
        cfg.setHysteria2Version(props.getProperty(AppConst.HYSTERIA2_VERSION));
        cfg.setHysteria2Port(props.getProperty(AppConst.HYSTERIA2_PORT));
        cfg.setRemarksPrefix(props.getProperty(AppConst.REMARKS_PREFIX));
        return cfg;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTuicVersion() {
        return tuicVersion;
    }

    public void setTuicVersion(String tuicVersion) {
        this.tuicVersion = tuicVersion;
    }

    public String getRemarksPrefix() {
        return remarksPrefix;
    }

    public void setRemarksPrefix(String remarksPrefix) {
        this.remarksPrefix = remarksPrefix;
    }

    public String getHysteria2Version() {
        return hysteria2Version;
    }

    public void setHysteria2Version(String hysteria2Version) {
        this.hysteria2Version = hysteria2Version;
    }

    public String getHysteria2Port() {
        return hysteria2Port;
    }

    public void setHysteria2Port(String hysteria2Port) {
        this.hysteria2Port = hysteria2Port;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }
}
