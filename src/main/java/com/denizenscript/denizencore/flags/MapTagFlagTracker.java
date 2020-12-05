package com.denizenscript.denizencore.flags;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.TimeTag;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.text.StringHolder;

import java.util.*;

public class MapTagFlagTracker extends AbstractFlagTracker {

    public MapTag map;

    public MapTagFlagTracker() {
        this.map = new MapTag();
    }

    public MapTagFlagTracker(MapTag map) {
        this.map = map;
        doClean(map);
    }

    public MapTagFlagTracker(String mapTagValue, TagContext context) {
        this(MapTag.valueOf(mapTagValue, context));
    }

    public static StringHolder valueString = new StringHolder("__value");

    public static StringHolder expirationString = new StringHolder("__expiration");

    public static boolean isExpired(ObjectTag expirationObj) {
        if (expirationObj == null) {
            return false;
        }
        if (TimeTag.now().millis() > ((TimeTag) expirationObj).millis()) {
            return true;
        }
        return false;
    }

    public ObjectTag getFlagValueOfType(String key, StringHolder type) {
        List<String> splitKey = CoreUtilities.split(key, '.');
        String endKey = splitKey.get(splitKey.size() - 1);
        MapTag map = this.map;
        for (int i = 0; i < splitKey.size() - 1; i++) {
            MapTag subMap = (MapTag) map.getObject(splitKey.get(i));
            if (subMap == null) {
                return null;
            }
            if (isExpired(subMap.map.get(expirationString))) {
                return null;
            }
            ObjectTag subValue = subMap.map.get(valueString);
            if (!(subValue instanceof MapTag)) {
                return null;
            }
            map = (MapTag) subValue;
        }
        MapTag obj = (MapTag) map.getObject(endKey);
        if (obj == null) {
            return null;
        }
        ObjectTag value = obj.map.get(type);
        if (value == null) {
            return null;
        }
        if (isExpired(obj.map.get(expirationString))) {
            return null;
        }
        if (value instanceof MapTag) {
            return deflaggedSubMap((MapTag) value);
        }
        return value;
    }

    @Override
    public ObjectTag getFlagValue(String key) {
        return getFlagValueOfType(key, valueString);
    }

    public MapTag deflaggedSubMap(MapTag map) {
        MapTag toReturn = new MapTag();
        for (Map.Entry<StringHolder, ObjectTag> pair : map.map.entrySet()) {
            MapTag subMap = (MapTag) pair.getValue();
            if (isExpired(subMap.map.get(expirationString))) {
                continue;
            }
            ObjectTag subValue = subMap.map.get(valueString);
            if (subValue instanceof MapTag) {
                subValue = deflaggedSubMap((MapTag) subValue);
            }
            toReturn.map.put(pair.getKey(), subValue);
        }
        return toReturn;
    }

    @Override
    public TimeTag getFlagExpirationTime(String key) {
        return (TimeTag) getFlagValueOfType(key, expirationString);
    }

    @Override
    public Collection<String> listAllFlags() {
        ArrayList<String> keys = new ArrayList<>(map.map.size());
        for (StringHolder string : map.map.keySet()) {
            keys.add(string.str);
        }
        return keys;
    }

    public void doClean(MapTag map) {
        ArrayList<StringHolder> toRemove = new ArrayList<>();
        for (Map.Entry<StringHolder, ObjectTag> entry : map.map.entrySet()) {
            if (isExpired(((MapTag) entry.getValue()).map.get(expirationString))) {
                toRemove.add(entry.getKey());
            }
            else {
                ObjectTag subValue = ((MapTag) entry.getValue()).map.get(valueString);
                if (subValue instanceof MapTag) {
                    doClean((MapTag) subValue);
                }
            }
        }
        for (StringHolder str : toRemove) {
            map.map.remove(str);
        }
    }

    public MapTag flaggifyMapTag(MapTag map) {
        MapTag toReturn = new MapTag();
        for (Map.Entry<StringHolder, ObjectTag> pair : map.map.entrySet()) {
            MapTag flagMap = new MapTag();
            if (pair.getValue() instanceof MapTag) {
                flagMap.map.put(valueString, flaggifyMapTag((MapTag) pair.getValue()));
            }
            else {
                flagMap.map.put(valueString, pair.getValue());
            }
            toReturn.map.put(pair.getKey(), flagMap);
        }
        return toReturn;
    }

    @Override
    public void setFlag(String key, ObjectTag value, TimeTag expiration) {
        List<String> splitKey = CoreUtilities.split(key, '.');
        String endKey = splitKey.get(splitKey.size() - 1);
        MapTag map = this.map;
        for (int i = 0; i < splitKey.size() - 1; i++) {
            MapTag flagMap = (MapTag) map.getObject(splitKey.get(i));
            if (flagMap == null) {
                flagMap = new MapTag();
                map.putObject(splitKey.get(i), flagMap);
            }
            ObjectTag innerMapTag = flagMap.map.get(valueString);
            flagMap.map.remove(expirationString);
            if (!(innerMapTag instanceof MapTag)) {
                innerMapTag = new MapTag();
                flagMap.map.put(valueString, innerMapTag);
            }
            map = (MapTag) innerMapTag;
        }
        MapTag resultMap = new MapTag();
        if (value != null) {
            if (value instanceof MapTag) {
                value = flaggifyMapTag((MapTag) value);
            }
            else if (value instanceof ElementTag && value.toString().startsWith("map@")) {
                MapTag mappified = MapTag.valueOf(value.toString(), CoreUtilities.noDebugContext);
                if (mappified != null) {
                    value = flaggifyMapTag(mappified);
                }
            }
            resultMap.map.put(valueString, value);
            if (expiration != null) {
                resultMap.map.put(expirationString, expiration);
            }
        }
        map.putObject(endKey, resultMap);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
