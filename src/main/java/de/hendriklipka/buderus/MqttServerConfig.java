package de.hendriklipka.buderus;

/**
 * User: hli
 * Date: 01.07.20
 * Time: 22:23
 */
public class MqttServerConfig
{
    private String _server;
    private int _port;
    private String _user;
    private String password;
    private String _clientId;
    private boolean _secure=false;

    public String getServer()
    {
        return _server;
    }

    public void setServer(final String server)
    {
        _server = server;
    }

    public int getPort()
    {
        return _port;
    }

    public void setPort(final int port)
    {
        _port = port;
    }

    public String getUser()
    {
        return _user;
    }

    public void setUser(final String user)
    {
        _user = user;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(final String password)
    {
        this.password = password;
    }

    public String getClientId()
    {
        return _clientId;
    }

    public void setClientId(final String clientId)
    {
        _clientId = clientId;
    }

    public boolean isSecure()
    {
        return _secure;
    }

    public void setSecure(final boolean secure)
    {
        _secure = secure;
    }
}
