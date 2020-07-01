package de.hendriklipka.buderus;

import java.util.List;

/**
 * User: hli
 * Date: 29.06.20
 * Time: 21:54
 */
public class Config
{
    private String _buderusServer;
    private String _privateKey;
    private String _gatewayPassword;
    private String _privatePassword;
    private String _md5Salt;

    private int _intervalSeconds;

    private List<MqttServerConfig> _mqttServers;
    private List<ServiceMapping> _services;

    public String getBuderusServer()
    {
        return _buderusServer;
    }

    public void setBuderusServer(final String buderusServer)
    {
        _buderusServer = buderusServer;
    }

    public String getPrivateKey()
    {
        return _privateKey;
    }

    public void setPrivateKey(final String privateKey)
    {
        _privateKey = privateKey;
    }

    public String getGatewayPassword()
    {
        return _gatewayPassword;
    }

    public void setGatewayPassword(final String gatewayPassword)
    {
        _gatewayPassword = gatewayPassword;
    }

    public String getPrivatePassword()
    {
        return _privatePassword;
    }

    public void setPrivatePassword(final String privatePassword)
    {
        _privatePassword = privatePassword;
    }

    public String getMd5Salt()
    {
        return _md5Salt;
    }

    public void setMd5Salt(final String md5Salt)
    {
        _md5Salt = md5Salt;
    }

    public int getIntervalSeconds()
    {
        return _intervalSeconds;
    }

    public void setIntervalSeconds(final int intervalSeconds)
    {
        _intervalSeconds = intervalSeconds;
    }

    public List<MqttServerConfig> getMqttServers()
    {
        return _mqttServers;
    }

    public void setMqttServers(final List<MqttServerConfig> mqttServers)
    {
        _mqttServers = mqttServers;
    }

    public List<ServiceMapping> getServices()
    {
        return _services;
    }

    public void setServices(final List<ServiceMapping> services)
    {
        this._services = services;
    }
}
