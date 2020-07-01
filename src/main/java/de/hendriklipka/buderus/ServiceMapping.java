package de.hendriklipka.buderus;

/**
 * User: hli
 * Date: 29.06.20
 * Time: 21:58
 */
public class ServiceMapping
{
    private String _serviceName;
    private String _mqttTopic;
    private String mqttMessage;
    private String _type;
    private int _qos=0;
    private boolean _retained=false;

    public String getServiceName()
    {
        return _serviceName;
    }

    public void setServiceName(final String serviceName)
    {
        _serviceName = serviceName;
    }

    public String getMqttTopic()
    {
        return _mqttTopic;
    }

    public void setMqttTopic(final String mqttTopic)
    {
        _mqttTopic = mqttTopic;
    }

    public String getMqttMessage()
    {
        return mqttMessage;
    }

    public void setMqttMessage(final String mqttMessage)
    {
        this.mqttMessage = mqttMessage;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(final String type)
    {
        _type = type;
    }

    public int getQos()
    {
        return _qos;
    }

    public void setQos(final int qos)
    {
        _qos = qos;
    }

    public boolean isRetained()
    {
        return _retained;
    }

    public void setRetained(final boolean retained)
    {
        _retained = retained;
    }
}
