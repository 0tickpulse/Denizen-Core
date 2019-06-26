package net.aufdemrand.denizencore.scripts.commands.core;

import net.aufdemrand.denizencore.DenizenCore;
import net.aufdemrand.denizencore.exceptions.InvalidArgumentsException;
import net.aufdemrand.denizencore.objects.Element;
import net.aufdemrand.denizencore.objects.aH;
import net.aufdemrand.denizencore.scripts.ScriptEntry;
import net.aufdemrand.denizencore.scripts.commands.AbstractCommand;
import net.aufdemrand.denizencore.utilities.CoreUtilities;
import net.aufdemrand.denizencore.utilities.debugging.dB;

import java.io.File;
import java.nio.file.Files;

public class FileCopyCommand extends AbstractCommand {

    // <--[command]
    // @Name FileCopy
    // @Syntax filecopy [origin:<origin>] [destination:<destination>] (overwrite)
    // @Required 2
    // @Short Copies a file from one location to another.
    // @Group core
    //
    // @Description
    // Copies a file from one location to another.
    // The starting directory is server/plugins/Denizen.
    // May overwrite existing copies of files.
    //
    // @Tags
    // <entry[saveName].success> returns whether the copy succeeded (if not, either an error or occurred, or there is an existing file in the destination.)
    //
    // @Usage
    // Use to copy a custom YAML data file to a backup folder, overwriting any old backup of it that exists.
    // - filecopy o:data/custom.yml d:data/backup.yml overwrite save:copy
    // - narrate "Copy success<&co> <entry[copy].success>"
    //
    // -->

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {


        for (aH.Argument arg : aH.interpretArguments(scriptEntry.aHArgs)) {

            if (!scriptEntry.hasObject("origin")
                    && arg.matchesPrefix("origin", "o")) {
                scriptEntry.addObject("origin", arg.asElement());
            }
            else if (!scriptEntry.hasObject("destination")
                    && arg.matchesPrefix("destination", "d")) {
                scriptEntry.addObject("destination", arg.asElement());
            }
            else if (!scriptEntry.hasObject("overwrite")
                    && arg.matches("overwrite")) {
                scriptEntry.addObject("overwrite", new Element("true"));
            }
            else {
                arg.reportUnhandled();
            }
        }

        if (!scriptEntry.hasObject("origin")) {
            throw new InvalidArgumentsException("Must have a valid origin!");
        }

        if (!scriptEntry.hasObject("destination")) {
            throw new InvalidArgumentsException("Must have a valid destination!");
        }

        scriptEntry.defaultObject("overwrite", new Element("false"));
    }

    @Override
    public void execute(final ScriptEntry scriptEntry) {
        Element origin = scriptEntry.getElement("origin");
        Element destination = scriptEntry.getElement("destination");
        Element overwrite = scriptEntry.getElement("overwrite");

        if (scriptEntry.dbCallShouldDebug()) {

            dB.report(scriptEntry, getName(), origin.debug() + destination.debug() + overwrite.debug());

        }

        if (!DenizenCore.getImplementation().allowFileCopy()) {
            dB.echoError(scriptEntry.getResidingQueue(), "File copy disabled by server administrator.");
            scriptEntry.addObject("success", new Element("false"));
            return;
        }

        File o = new File(DenizenCore.getImplementation().getDataFolder(), origin.asString());
        File d = new File(DenizenCore.getImplementation().getDataFolder(), destination.asString());
        boolean ow = overwrite.asBoolean();
        boolean dexists = d.exists();
        boolean disdir = d.isDirectory() || destination.asString().endsWith("/");

        if (!DenizenCore.getImplementation().canReadFile(o)) {
            dB.echoError("Server config denies reading files in that location.");
            return;
        }
        if (!o.exists()) {
            dB.echoError(scriptEntry.getResidingQueue(), "File copy failed, origin does not exist!");
            scriptEntry.addObject("success", new Element("false"));
            return;
        }

        if (!DenizenCore.getImplementation().canWriteToFile(d)) {
            dB.echoError(scriptEntry.getResidingQueue(), "Can't copy files to there!");
            scriptEntry.addObject("success", new Element("false"));
            return;
        }

        if (dexists && !disdir && !ow) {
            dB.echoDebug(scriptEntry, "File copy ignored, destination file already exists!");
            scriptEntry.addObject("success", new Element("false"));
            return;
        }
        try {
            if (dexists && !disdir) {
                d.delete();
            }
            if (disdir && !dexists) {
                d.mkdirs();
            }
            if (o.isDirectory()) {
                CoreUtilities.copyDirectory(o, d);
            }
            else {
                Files.copy(o.toPath(), (disdir ? d.toPath().resolve(o.toPath().getFileName()) : d.toPath()));
            }
            scriptEntry.addObject("success", new Element("true"));
        }
        catch (Exception e) {
            dB.echoError(scriptEntry.getResidingQueue(), e);
            scriptEntry.addObject("success", new Element("false"));
            return;
        }
    }
}
