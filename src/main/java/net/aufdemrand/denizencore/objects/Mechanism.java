package net.aufdemrand.denizencore.objects;

import net.aufdemrand.denizencore.utilities.debugging.dB;

public class Mechanism {

    private boolean fulfilled;
    private String raw_mechanism;
    private Element value;
    private String outcome = null;

    public Mechanism(Element mechanism, Element value) {
        fulfilled = false;
        raw_mechanism = mechanism.asString();
        this.value = value;
    }

    public void fulfill(String _outcome) {
        fulfilled = true;
        outcome = _outcome; // TODO: Return outcome somewhere?
    }

    public boolean fulfilled() {
        return fulfilled;
    }

    public String getName() {
        return raw_mechanism;
    }

    public Element getValue() {
        if (value == null) {
            return new Element("");
        }
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public boolean matches(String string) {
        if (string.equalsIgnoreCase(raw_mechanism)) {
            fulfill("");
            return true;
        }
        return false;
    }

    public boolean requireBoolean() {
        return requireBoolean("Invalid boolean. Must specify TRUE or FALSE.");
    }

    public boolean requireDouble() {
        return requireDouble("Invalid double specified.");
    }

    public boolean requireEnum(boolean allowInt, Enum<?>... values) {
        return requireEnum(null, allowInt, values);
    }

    public boolean requireFloat() {
        return requireFloat("Invalid float specified.");
    }

    public boolean requireInteger() {
        return requireInteger("Invalid integer specified.");
    }

    public <T extends dObject> boolean requireObject(Class<T> type) {
        return requireObject(null, type);
    }

    public boolean requireBoolean(String error) {
        if (hasValue() && value.isBoolean()) {
            return true;
        }
        dB.echoError(error);
        return false;
    }

    public boolean requireDouble(String error) {
        if (value.isDouble()) {
            return true;
        }
        dB.echoError(error);
        return false;
    }

    public boolean requireEnum(String error, boolean allowInt, Enum<?>... values) {
        if (hasValue() && allowInt && value.isInt() && value.asInt() < values.length) {
            return true;
        }
        if (hasValue() && value.isString()) {
            String raw_value = value.asString().toUpperCase();
            for (Enum<?> check_value : values) {
                if (raw_value.equals(check_value.name())) {
                    return true;
                }
            }
        }
        if (error == null) {
            dB.echoError("Invalid " + values[0].getDeclaringClass().getSimpleName() + "."
                    + " Must specify a valid name" + (allowInt ? " or number" : "") + ".");
        }
        else {
            dB.echoError(error);
        }
        return false;
    }

    public boolean requireFloat(String error) {
        if (hasValue() && value.isFloat()) {
            return true;
        }
        dB.echoError(error);
        return false;
    }

    public boolean requireInteger(String error) {
        if (hasValue() && value.isInt()) {
            return true;
        }
        dB.echoError(error);
        return false;
    }

    public <T extends dObject> boolean requireObject(String error, Class<T> type) {
        if (hasValue() && value.matchesType(type)) {
            return true;
        }
        if (error == null) {
            // TODO: Remove getSimpleName(), or simplify somehow.
            dB.echoError("Invalid " + type.getSimpleName() + " specified.");
        }
        else {
            dB.echoError(error);
        }
        return false;
    }

    public void reportInvalid() {
        dB.echoError("Invalid mechanism specified: " + raw_mechanism);
    }
}
