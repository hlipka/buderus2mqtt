package de.hendriklipka.buderus;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: hli
 * Date: 22.06.20
 * Time: 22:12
 */
public class MessageParameters
{
    private static final Logger logger = LoggerFactory.getLogger(MessageParameters.class);

    String _service;
    String _valueS;
    double _valueD;
    boolean _valueB;
    int _valueI;

    public MessageParameters(final String service, final ServiceType type, final Object value)
    {
        _service = service;
        switch (type)
        {
            case STRING:
                _valueS = value.toString();
                _valueB = Boolean.parseBoolean(_valueS);
                try
                {
                    _valueD = Double.parseDouble(_valueS);
                }
                catch (NumberFormatException ignored)
                {
                }
                try
                {
                    _valueI = Integer.parseInt(_valueS);
                }
                catch (NumberFormatException ignored)
                {
                }
                break;
            case FLOAT:
                try
                {
                    _valueD = Double.parseDouble(value.toString());
                }
                catch (NumberFormatException ignored)
                {
                    logger.warn("Cannot parse the message value '{}' into a double value.", value.toString());
                }
                _valueI = (int) _valueD;
                _valueS = Double.toString(_valueD);
                _valueB = 0 != _valueD;
                break;
        }
    }

    // pattern: {address} {value_i} {value_d} {value_s} {value_b}
    public String replace(String message)
    {
        String result = message;
        result = result.replace("{service}", _service);
        result = result.replace("{value_i}", Integer.toString(_valueI));
        result = result.replace("{value_d}", Double.toString(_valueD));
        result = result.replace("{value_b}", Boolean.toString(_valueB));
        result = result.replace("{value_s}", _valueS);
        return result;
    }
}
