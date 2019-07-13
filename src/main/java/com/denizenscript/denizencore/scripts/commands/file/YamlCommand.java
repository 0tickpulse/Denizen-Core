package com.denizenscript.denizencore.scripts.commands.file;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.YamlConfiguration;
import com.denizenscript.denizencore.utilities.debugging.dB;
import com.denizenscript.denizencore.utilities.scheduling.AsyncSchedulable;
import com.denizenscript.denizencore.utilities.scheduling.OneTimeSchedulable;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptHelper;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ReplaceableTagEvent;
import com.denizenscript.denizencore.tags.TagManager;
import org.json.JSONObject;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

public class YamlCommand extends AbstractCommand implements Holdable {

    // <--[command]
    // @Name Yaml
    // @Syntax yaml [create]/[load:<file> (fix_formatting)]/[loadtext:<text> (fix_formatting)]/[unload]/[savefile:<file>]/[copykey:<source key> <target key> (to_id:<name>)]/[set <key>([<#>])(:<action>):<value>] [id:<name>]
    // @Required 2
    // @Short Edits a YAML configuration file.
    // @Group file
    //
    // @Description
    // Edits a YAML configuration file.
    // This can be used for interacting with other plugins' configuration files.
    // It can also be used for storing your own script's data.
    // TODO: Document Command Details
    // When loading a script, optionally add 'fix_formatting' to run the file through
    // Denizen's built in script preparser to correct common YAML errors,
    // such as tabs instead of spaces or comments inside braced blocks.
    // Use holdable syntax ("- ~yaml load:...") with load or savefile actions to avoid locking up the server during file IO.
    //
    // For loading and saving, the starting path is within 'plugins/Denizen'.
    // Note that the '.yml' extension is not automatically appended, and you will have to include that in filenames.
    //
    // @Tags
    // <yaml[<idname>].contains[<path>]>
    // <yaml[<idname>].read[<path>]>
    // <yaml[<idname>].read[<path>].as_list>
    // <yaml[<idname>].list_keys[<path>]>
    //
    // @Usage
    // Use to create a new YAML file.
    // - yaml create id:myfile
    //
    // @Usage
    // Use to load a YAML file from disk.
    // - yaml load:myfile.yml id:myfile
    //
    // @Usage
    // Use to modify a YAML file similarly to a flag.
    // - yaml id:myfile set my.key:HelloWorld
    //
    // @Usage
    // Use to save a YAML file to disk.
    // - yaml savefile:myfile.yml id:myfile
    //
    // @Usage
    // Use to unload a YAML file from memory.
    // - yaml unload id:myfile
    //
    // @Usage
    // Use to modify a YAML file similarly to a flag.
    // - yaml id:myfile set my.key:+:2
    //
    // @Usage
    // Use to modify a YAML file similarly to a flag.
    // - yaml id:myfile set my.key[2]:hello
    //
    // @Usage
    // Use to modify a copy the contents of one YAML key to a new owning key.
    // - yaml id:myfile copykey:my.first.key my.new.key
    //
    // @Usage
    // Use to modify a copy the contents of one YAML key to a new owning key on a different YAML file.
    // - yaml id:myfile copykey:my.first.key my.new.key to_id:myotherfile
    // -->

    @Override
    public void onEnable() {
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                yaml(event);
            }
        }, "yaml");
    }

    Map<String, YamlConfiguration> yamls = new HashMap<>();

    private YamlConfiguration getYaml(String id) {
        if (id == null) {
            dB.echoError("Trying to get YAML file with NULL ID!");
            return null;
        }
        return yamls.get(id.toUpperCase());
    }

    public enum Action {LOAD, LOADTEXT, UNLOAD, CREATE, WRITE, SAVE, SET, COPYKEY}

    public enum YAML_Action {
        SET_VALUE, INCREASE, DECREASE, MULTIPLY,
        DIVIDE, INSERT, REMOVE, SPLIT, DELETE, SPLIT_NEW
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {

        boolean isSet = false;

        for (aH.Argument arg : aH.interpretArguments(scriptEntry.aHArgs)) {
            if (!scriptEntry.hasObject("action") &&
                    arg.matchesPrefix("load")) {
                scriptEntry.addObject("action", new Element("LOAD"));
                scriptEntry.addObject("filename", arg.asElement());
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matchesPrefix("loadtext")) {
                scriptEntry.addObject("action", new Element("LOADTEXT"));
                scriptEntry.addObject("raw_text", arg.asElement());
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matchesPrefix("savefile", "filesave")) {
                scriptEntry.addObject("action", new Element("SAVE"));
                scriptEntry.addObject("filename", arg.asElement());
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matches("create")) {
                scriptEntry.addObject("action", new Element("CREATE"));
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matches("set")) {
                scriptEntry.addObject("action", new Element("SET"));
                isSet = true;
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matchesPrefix("copykey")) {
                scriptEntry.addObject("action", new Element("COPYKEY"));
                scriptEntry.addObject("key", arg.asElement());
                isSet = true;
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matches("unload")) {
                scriptEntry.addObject("action", new Element("UNLOAD"));
            }
            else if (!scriptEntry.hasObject("action") &&
                    arg.matchesPrefix("write")) {
                dB.echoError(scriptEntry.getResidingQueue(), "YAML write is deprecated, use YAML set!");
                scriptEntry.addObject("action", new Element("WRITE"));
                scriptEntry.addObject("key", arg.asElement());
            }
            else if (!scriptEntry.hasObject("value") &&
                    arg.matchesPrefix("value")) {
                if (arg.matchesArgumentType(dList.class)) {
                    scriptEntry.addObject("value", arg.asType(dList.class));
                }
                else {
                    scriptEntry.addObject("value", arg.asElement());
                }
            }
            else if (!scriptEntry.hasObject("id") &&
                    arg.matchesPrefix("id")) {
                scriptEntry.addObject("id", arg.asElement());
            }
            else if (!scriptEntry.hasObject("to_id") &&
                    arg.matchesPrefix("to_id")) {
                scriptEntry.addObject("to_id", arg.asElement());
            }
            else if (!scriptEntry.hasObject("split") &&
                    arg.matches("split_list")) {
                scriptEntry.addObject("split", new Element("true"));
            }
            else if (!scriptEntry.hasObject("fix_formatting") &&
                    arg.matches("fix_formatting")) {
                scriptEntry.addObject("fix_formatting", new Element("true"));
            }

            // Check for key:value/action
            else if (isSet &&
                    !scriptEntry.hasObject("value") &&
                    arg.raw_value.split(":", 3).length == 2) {

                String[] flagArgs = arg.raw_value.split(":", 2);
                scriptEntry.addObject("key", new Element(flagArgs[0]));

                if (flagArgs[1].equals("++") || flagArgs[1].equals("+")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.INCREASE);
                    scriptEntry.addObject("value", new Element(1));
                }
                else if (flagArgs[1].equals("--") || flagArgs[1].equals("-")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.DECREASE);
                    scriptEntry.addObject("value", new Element(1));
                }
                else if (flagArgs[1].equals("!")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.DELETE);
                    scriptEntry.addObject("value", new Element(false));
                }
                else if (flagArgs[1].equals("<-")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.REMOVE);
                    scriptEntry.addObject("value", new Element(false));
                }
                else {
                    // No ACTION, we're just setting a value...
                    scriptEntry.addObject("yaml_action", YAML_Action.SET_VALUE);
                    scriptEntry.addObject("value", new Element(flagArgs[1]));
                }
            }

            // Check for key:action:value
            else if (isSet &&
                    !scriptEntry.hasObject("value") &&
                    arg.raw_value.split(":", 3).length == 3) {
                String[] flagArgs = arg.raw_value.split(":", 3);
                scriptEntry.addObject("key", new Element(flagArgs[0]));

                if (flagArgs[1].equals("->")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.INSERT);
                }
                else if (flagArgs[1].equals("<-")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.REMOVE);
                }
                else if (flagArgs[1].equals("||") || flagArgs[1].equals("|")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.SPLIT);
                }
                else if (flagArgs[1].equals("!|")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.SPLIT_NEW);
                }
                else if (flagArgs[1].equals("++") || flagArgs[1].equals("+")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.INCREASE);
                }
                else if (flagArgs[1].equals("--") || flagArgs[1].equals("-")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.DECREASE);
                }
                else if (flagArgs[1].equals("**") || flagArgs[1].equals("*")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.MULTIPLY);
                }
                else if (flagArgs[1].equals("//") || flagArgs[1].equals("/")) {
                    scriptEntry.addObject("yaml_action", YAML_Action.DIVIDE);
                }
                else {
                    scriptEntry.addObject("yaml_action", YAML_Action.SET_VALUE);
                    scriptEntry.addObject("value", new Element(arg.raw_value.split(":", 2)[1]));
                    continue;
                }
                scriptEntry.addObject("value", new Element(flagArgs[2]));
            }
            else {
                arg.reportUnhandled();
            }
        }

        // Check for required arguments

        if (!scriptEntry.hasObject("id")) {
            throw new InvalidArgumentsException("Must specify an id!");
        }

        if (!scriptEntry.hasObject("action")) {
            throw new InvalidArgumentsException("Must specify an action!");
        }

        if (!scriptEntry.hasObject("key") &&
                scriptEntry.getElement("action").asString().equalsIgnoreCase("write")) {
            throw new InvalidArgumentsException("Must specify a key!");
        }

        scriptEntry.defaultObject("value", new Element(""));
        scriptEntry.defaultObject("fix_formatting", new Element("false"));
    }


    @Override
    public void execute(final ScriptEntry scriptEntry) {

        Element filename = scriptEntry.getElement("filename");
        Element rawText = scriptEntry.getElement("raw_text");
        Element key = scriptEntry.getElement("key");
        dObject value = scriptEntry.getdObject("value");
        Element split = scriptEntry.getElement("split");
        YAML_Action yaml_action = (YAML_Action) scriptEntry.getObject("yaml_action");
        Element actionElement = scriptEntry.getElement("action");
        Element idElement = scriptEntry.getElement("id");
        Element fixFormatting = scriptEntry.getElement("fix_formatting");
        Element toId = scriptEntry.getElement("to_id");

        YamlConfiguration yamlConfiguration;

        if (scriptEntry.dbCallShouldDebug()) {

            dB.report(scriptEntry, getName(),
                    idElement.debug()
                            + actionElement.debug()
                            + (filename != null ? filename.debug() : "")
                            + (yaml_action != null ? aH.debugObj("yaml_action", yaml_action.name()) : "")
                            + (key != null ? key.debug() : "")
                            + (value != null ? value.debug() : "")
                            + (split != null ? split.debug() : "")
                            + (rawText != null ? rawText.debug() : "")
                            + (toId != null ? toId.debug() : "")
                            + fixFormatting.debug());

        }

        // Do action
        Action action = Action.valueOf(actionElement.asString().toUpperCase());
        String id = idElement.asString().toUpperCase();

        if (action != Action.LOAD && action != Action.SAVE && scriptEntry.shouldWaitFor()) {
            scriptEntry.setFinished(true);
        }
        switch (action) {

            case LOAD:
                File file = new File(DenizenCore.getImplementation().getDataFolder(), filename.asString());
                if (!DenizenCore.getImplementation().canReadFile(file)) {
                    dB.echoError("Server config denies reading files in that location.");
                    scriptEntry.setFinished(true);
                    return;
                }
                if (!file.exists()) {
                    dB.echoError("File cannot be found!");
                    scriptEntry.setFinished(true);
                    return;
                }
                YamlConfiguration[] runnableConfigs = new YamlConfiguration[1];
                Runnable onLoadCompleted = new Runnable() {
                    @Override
                    public void run() {
                        if (yamls.containsKey(id)) {
                            yamls.remove(id);
                        }
                        yamls.put(id, runnableConfigs[0]);
                        scriptEntry.setFinished(true);
                    }
                };
                Runnable loadRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileInputStream fis = new FileInputStream(file);
                            String str = ScriptHelper.convertStreamToString(fis);
                            if (fixFormatting.asBoolean()) {
                                str = ScriptHelper.ClearComments("", str, false);
                            }
                            runnableConfigs[0] = YamlConfiguration.load(str);
                            fis.close();
                            if (runnableConfigs[0] == null) {
                                runnableConfigs[0] = new YamlConfiguration();
                            }
                            if (scriptEntry.shouldWaitFor()) {
                                DenizenCore.schedule(new OneTimeSchedulable(onLoadCompleted, 0));
                            }
                            else {
                                onLoadCompleted.run();
                            }
                        }
                        catch (Exception e) {
                            dB.echoError("Failed to load yaml file: " + e);
                        }
                    }
                };
                if (scriptEntry.shouldWaitFor()) {
                    DenizenCore.schedule(new AsyncSchedulable(new OneTimeSchedulable(loadRunnable, 0)));
                }
                else {
                    loadRunnable.run();
                }
                break;

            case LOADTEXT:
                String str = rawText.asString();
                if (fixFormatting.asBoolean()) {
                    str = ScriptHelper.ClearComments("", str, false);
                }
                YamlConfiguration config = YamlConfiguration.load(str);
                if (yamls.containsKey(id)) {
                    yamls.remove(id);
                }
                yamls.put(id, config);
                scriptEntry.setFinished(true);
                break;

            case UNLOAD:
                if (yamls.containsKey(id)) {
                    yamls.remove(id);
                }
                else {
                    dB.echoError("Unknown YAML ID '" + id + "'");
                }
                break;

            case SAVE:
                if (yamls.containsKey(id)) {
                    try {
                        if (!DenizenCore.getImplementation().allowStrangeYAMLSaves()) {
                            File fileObj = new File(DenizenCore.getImplementation().
                                    getDataFolder().getAbsolutePath() + "/" + filename.asString());
                            String directory = URLDecoder.decode(System.getProperty("user.dir"));
                            if (!fileObj.getCanonicalPath().startsWith(directory)) {
                                dB.echoError("Outside-the-main-folder YAML saves disabled by administrator.");
                                scriptEntry.setFinished(true);
                                return;
                            }
                        }
                        File fileObj = new File(DenizenCore.getImplementation().
                                getDataFolder().getAbsolutePath() + "/" + filename.asString());
                        if (!DenizenCore.getImplementation().canWriteToFile(fileObj)) {
                            dB.echoError(scriptEntry.getResidingQueue(), "Cannot edit that file!");
                            scriptEntry.setFinished(true);
                            return;
                        }
                        fileObj.getParentFile().mkdirs();
                        String outp = yamls.get(id).saveToString();
                        Runnable saveRunnable = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    FileWriter fw = new FileWriter(fileObj.getAbsoluteFile());
                                    BufferedWriter writer = new BufferedWriter(fw);
                                    writer.write(outp);
                                    writer.close();
                                    fw.close();
                                }
                                catch (IOException e) {
                                    dB.echoError(e);
                                }
                                scriptEntry.setFinished(true);
                            }
                        };
                        if (scriptEntry.shouldWaitFor()) {
                            DenizenCore.schedule(new AsyncSchedulable(new OneTimeSchedulable(saveRunnable, 0)));
                        }
                        else {
                            saveRunnable.run();
                        }
                    }
                    catch (IOException e) {
                        dB.echoError(e);
                    }
                }
                else {
                    dB.echoError("Unknown YAML ID '" + id + "'");
                    scriptEntry.setFinished(true);
                }
                break;

            case WRITE:
                if (yamls.containsKey(id)) {
                    if (value instanceof Element) {
                        yamls.get(id).set(key.asString(), ((Element) value).asString());
                    }
                    else if (split != null && split.asBoolean()) {
                        yamls.get(id).set(key.asString(), value);
                    }
                    else {
                        yamls.get(id).set(key.asString(), value.identify());
                    }
                }
                else {
                    dB.echoError("Unknown YAML ID '" + id + "'");
                }
                break;

            case COPYKEY: {
                if (!yamls.containsKey(id)) {
                    break;
                }
                YamlConfiguration yaml = yamls.get(id);
                YamlConfiguration destYaml = yaml;
                if (toId != null) {
                    if (yamls.containsKey(toId.toString().toUpperCase())) {
                        destYaml = yamls.get(toId.toString().toUpperCase());
                    }
                    else {
                        dB.echoError("Unknown YAML TO-ID '" + id + "'");
                        break;
                    }
                }
                YamlConfiguration sourceSection = yaml.getConfigurationSection(key.asString());
                YamlConfiguration newSection = copySection(sourceSection);
                destYaml.set(value.toString(), newSection);
                break;
            }

            case SET:
                if (yamls.containsKey(id)) {
                    if (yaml_action == null || key == null || value == null) {
                        dB.echoError("Must specify a YAML action and value!");
                        return;
                    }
                    YamlConfiguration yaml = yamls.get(id);

                    int index = -1;
                    if (key.asString().contains("[")) {
                        try {
                            if (dB.verbose) {
                                dB.echoDebug(scriptEntry, "Try index: " + key.asString().split("\\[")[1].replace("]", ""));
                            }
                            index = Integer.valueOf(key.asString().split("\\[")[1].replace("]", "")) - 1;
                        }
                        catch (Exception e) {
                            if (dB.verbose) {
                                dB.echoError(scriptEntry.getResidingQueue(), e);
                            }
                            index = -1;
                        }
                        key = Element.valueOf(key.asString().split("\\[")[0]);
                    }

                    String keyStr = key.asString();
                    String valueStr = value.identify();

                    switch (yaml_action) {
                        case INCREASE:
                            Set(yaml, index, keyStr, CoreUtilities.doubleToString(aH.getDoubleFrom(Get(yaml, index, keyStr, "0")) + aH.getDoubleFrom(valueStr)));
                            break;
                        case DECREASE:
                            Set(yaml, index, keyStr, CoreUtilities.doubleToString(aH.getDoubleFrom(Get(yaml, index, keyStr, "0")) - aH.getDoubleFrom(valueStr)));
                            break;
                        case MULTIPLY:
                            Set(yaml, index, keyStr, CoreUtilities.doubleToString(aH.getDoubleFrom(Get(yaml, index, keyStr, "1")) * aH.getDoubleFrom(valueStr)));
                            break;
                        case DIVIDE:
                            Set(yaml, index, keyStr, CoreUtilities.doubleToString(aH.getDoubleFrom(Get(yaml, index, keyStr, "1")) / aH.getDoubleFrom(valueStr)));
                            break;
                        case DELETE:
                            yaml.set(keyStr, null);
                            break;
                        case SET_VALUE:
                            Set(yaml, index, keyStr, valueStr);
                            break;
                        case INSERT: {
                            List<String> list = yaml.getStringList(keyStr);
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            list.add(valueStr);
                            yaml.set(keyStr, list);
                            break;
                        }
                        case REMOVE: {
                            List<String> list = yaml.getStringList(keyStr);
                            if (list == null) {
                                if (dB.verbose) {
                                    dB.echoDebug(scriptEntry, "List null!");
                                }
                                break;
                            }
                            if (index > -1 && index < list.size()) {
                                if (dB.verbose) {
                                    dB.echoDebug(scriptEntry, "Remove ind: " + index);
                                }
                                list.remove(index);
                                yaml.set(keyStr, list);
                            }
                            else {
                                if (dB.verbose) {
                                    dB.echoDebug(scriptEntry, "Remvoe value: " + valueStr);
                                }
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i).equalsIgnoreCase(valueStr)) {
                                        list.remove(i);
                                        break;
                                    }
                                }
                                yaml.set(keyStr, list);
                                break;
                            }
                            break;
                        }
                        case SPLIT_NEW: {
                            yaml.set(keyStr, new ArrayList<>(dList.valueOf(valueStr)));
                            break;
                        }
                        case SPLIT: {
                            List<String> list = yaml.getStringList(keyStr);
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            list.addAll(dList.valueOf(valueStr));
                            yaml.set(keyStr, list);
                            break;
                        }
                    }
                }
                else {
                    dB.echoError("Unknown YAML ID '" + id + "'");
                }
                break;

            case CREATE:
                if (yamls.containsKey(id)) {
                    yamls.remove(id);
                }
                yamlConfiguration = new YamlConfiguration();
                yamls.put(id.toUpperCase(), yamlConfiguration);
                break;
        }

    }

    public YamlConfiguration copySection(YamlConfiguration section) {
        YamlConfiguration newSection = new YamlConfiguration();
        for (StringHolder key : section.getKeys(false)) {
            Object obj = section.get(key.str);
            if (obj instanceof YamlConfiguration) {
                obj = copySection((YamlConfiguration) obj);
            }
            newSection.set(key.str, obj);
        }
        return newSection;
    }

    public String Get(YamlConfiguration yaml, int index, String key, String def) {
        if (index == -1) {
            return yaml.getString(key, def);
        }
        else {
            List<String> list = yaml.getStringList(key);
            if (index < 0) {
                index = 0;
            }
            if (index > list.size()) {
                index = list.size() - 1;
            }
            if (list.size() == 0) {
                return "";
            }
            return list.get(index);
        }
    }

    public void Set(YamlConfiguration yaml, int index, String key, String value) {
        if (index == -1) {
            yaml.set(key, value);
        }
        else {
            List<String> list = yaml.getStringList(key);
            if (list == null) {
                list = new ArrayList<>();
            }
            if (index < 0) {
                index = 0;
            }
            if (index >= list.size()) {
                list.add(value);
            }
            else {
                list.set(index, value);
            }
            yaml.set(key, list);
        }
    }

    public void yaml(ReplaceableTagEvent event) {

        if (!event.matches("yaml")) {
            return;
        }

        Attribute attribute = event.getAttributes();

        // <--[tag]
        // @attribute <yaml.list>
        // @returns dList
        // @description
        // Returns a list of all currently loaded YAML ID's.
        // -->
        if (attribute.getAttribute(2).equalsIgnoreCase("list")) {
            dList list = new dList();
            list.addAll(yamls.keySet());
            event.setReplaced(list.getAttribute(attribute.fulfill(2)));
            return;
        }

        // YAML tag requires name context and type context.
        if ((!event.hasNameContext() || !(event.hasTypeContext() || attribute.getAttribute(2).equalsIgnoreCase("to_json")))
                && !attribute.hasAlternative()) {
            dB.echoError("YAML tag '" + event.raw_tag + "' is missing required context. Tag replacement aborted.");
            return;
        }

        // Set id (name context) and path (type context)
        String id = event.getNameContext().toUpperCase();
        String path = event.getTypeContext();

        // Check if there is a yaml file loaded with the specified id
        if (!yamls.containsKey(id)) {
            if (!attribute.hasAlternative()) {
                dB.echoError("YAML tag '" + event.raw_tag + "' has specified an invalid ID, or the specified id has already" +
                        " been closed. Tag replacement aborted. ID given: '" + id + "'.");
            }
            return;
        }

        // Catch up with what has already been processed.
        attribute.fulfill(1);

        //
        // Check attributes
        //

        // <--[tag]
        // @attribute <yaml[<id>].contains[<path>]>
        // @returns Element(Boolean)
        // @description
        // Returns true if the file has the specified path.
        // Otherwise, returns false.
        // -->
        if (attribute.startsWith("contains")) {
            event.setReplaced(new Element(getYaml(id).contains(path))
                    .getAttribute(attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <yaml[<id>].is_list[<path>]>
        // @returns Element(Boolean)
        // @description
        // Returns true if the specified path results in a list.
        // -->
        if (attribute.startsWith("is_list")) {
            event.setReplaced(new Element(getYaml(id).isList(path))
                    .getAttribute(attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <yaml[<id>].read[<path>]>
        // @returns Element
        // @description
        // Returns the value of the key at the path.
        // If the key is a list, returns a dList instead.
        // -->
        if (attribute.startsWith("read")) {
            attribute.fulfill(1);

            if (getYaml(id).isList(path)) {
                List<String> value = getYaml(id).getStringList(path);
                if (value == null) {
                    // If value is null, the key at the specified path didn't exist.
                    return;
                }
                else {
                    event.setReplaced(new dList(value).getAttribute(attribute));
                    return;
                }
            }
            else {
                String value = getYaml(id).getString(path);
                if (value == null) {
                    // If value is null, the key at the specified path didn't exist.
                    return;
                }
                else {
                    event.setReplaced(new Element(value).getAttribute(attribute));
                    return;
                }
            }
        }

        // <--[tag]
        // @attribute <yaml[<id>].list_deep_keys[<path>]>
        // @returns dList
        // @description
        // Returns a dList of all the keys at the path and all subpaths.
        // -->
        if (attribute.startsWith("list_deep_keys")) {
            Set<StringHolder> keys;
            if (path != null && path.length() > 0) {
                YamlConfiguration section = getYaml(id).getConfigurationSection(path);
                if (section == null) {
                    return;
                }
                keys = section.getKeys(true);
            }
            else {
                keys = getYaml(id).getKeys(true);
            }
            if (keys == null) {
                return;

            }
            else {
                event.setReplaced(new dList(keys).getAttribute(attribute.fulfill(1)));
                return;
            }
        }

        // <--[tag]
        // @attribute <yaml[<id>].list_keys[<path>]>
        // @returns dList
        // @description
        // Returns a dList of all the keys at the path.
        // -->
        if (attribute.startsWith("list_keys")) {
            Set<StringHolder> keys;
            if (path != null && path.length() > 0) {
                YamlConfiguration section = getYaml(id).getConfigurationSection(path);
                if (section == null) {
                    return;
                }
                keys = section.getKeys(false);
            }
            else {
                keys = getYaml(id).getKeys(false);
            }
            if (keys == null) {
                return;

            }
            else {
                event.setReplaced(new dList(keys).getAttribute(attribute.fulfill(1)));
                return;
            }
        }

        // <--[tag]
        // @attribute <yaml[<id>].to_json>
        // @returns Element
        // @description
        // Converts the YAML container to a JSON array.
        // -->
        if (attribute.startsWith("to_json")) {
            JSONObject jsobj = new JSONObject(getYaml(id).getMap());
            event.setReplaced(new Element(jsobj.toString()).getAttribute(attribute.fulfill(1)));
            return;
        }

        // <--[tag]
        // @attribute <yaml[<id>].to_text>
        // @returns Element
        // @description
        // Converts the YAML container to raw YAML text.
        // -->
        if (attribute.startsWith("to_text")) {
            event.setReplaced(new Element(getYaml(id).saveToString()).getAttribute(attribute.fulfill(1)));
            return;
        }
    }
}
