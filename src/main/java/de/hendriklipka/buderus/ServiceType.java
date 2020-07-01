package de.hendriklipka.buderus;

/**
 * User: hli
 * Date: 29.06.20
 * Time: 22:03
 */
public enum ServiceType
{
    FLOAT("floatValue"),
    STRING("stringValue"),
    ;

    private final String _typeName;

    ServiceType(final String typeName)
    {
        _typeName = typeName;
    }

    public String getTypeName()
    {
        return _typeName;
    }
}
