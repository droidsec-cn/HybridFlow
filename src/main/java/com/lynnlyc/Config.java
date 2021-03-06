// Configurations of webview analysis tool
/*
 * This config class contains the important options
 * which might be used in both app and web analysis
 * For app analysis, it configures soot tool by operating on Options.v()
 * For web analysis, it stores the javascript bridges and temp js file paths
 *  generated during app analysis
 */

package com.lynnlyc;

import org.apache.commons.cli.*;
import org.apache.commons.cli.Option;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import soot.options.Options;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by yuanchun on 5/4/15.
 * Package: webview-flow
 */
public class Config {
    public static final String projectName = "HybridFlow";

    // File path of apk
    public static String appFilePath = "";

    // File path of source to sink definition
    public static String sourceAndSinkFilePath = "";

    // Directory path to find android.jar
    public static String androidPlatformDir = "";

    // File path of android.jar which is forced to use by soot
    public static String forceAndroidJarPath = "";

    // Directory path of app html side
    public static String htmlDirPath = "";
    // Directory path of app java side
    public static String javaDirPath = "";
    // Directory path of bridges
    public static String bridgeDirPath = "";

    // Directory for result output or wirking dir
    public static String workingDirPath = "";

    // Output format
    public static String outputFormat = "dex";

    private static PrintStream logPs;
    private static PrintStream bridgePs;

    public static final int TAINT_ANALYSIS_TIMEOUT_SECONDS = 180;

    // noticeable webview methods
    public static String[] webview_methods = {
        "addJavascriptInterface",
        "loadUrl",
        "evaluateJavascript",
        "loadData",
        "loadDataWithBaseURL"
    };

    // possible app entries, used for generating appEntryPoints
    public static String[] possible_entries = {
        "onCreate",
        "onStart",
        "onCreateView",
        "onClick",
    };

    public static boolean runJSA = true;
    public static boolean runPTA = true;

    public static final String modeAll = "All";
    public static final String modeBuildBridge = "BuildBridge";
    public static final String modeRunTaintAnalysis = "RunTaintAnalysis";
    public static final String modeMergeTaintFlow = "MergeTaintFlow";
    public static final String modeDescription = String.format("available modes:\n[%s] %s\n[%s] %s\n[%s] %s\n[%s] %s\n",
            modeBuildBridge, "Step1: extract hybrid bridges of app and generate a HybridFlow directory",
            modeRunTaintAnalysis, "Step2: run taint analysis of java and HTML respectively",
            modeMergeTaintFlow, "Step3: merge the respective taint flows to hybrid taint flows",
            modeAll, "Run step 1-3 at once. (default)");

    public static String mode = modeAll;
    public static boolean isHybridApp = true;

    public static boolean parseArgs(String[] args) {
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();

        Option outputDirOpt = new Option("d", true, "path to output/working dir");
        outputDirOpt.setArgName("dir");
        options.addOption(outputDirOpt);

        Option appPathOpt = new Option("i", true, "path to input target apk file");
        appPathOpt.setArgName("apk");
        options.addOption(appPathOpt);

        Option androidPlatformDirOpt = new Option("sdk", true, "path to android sdk platforms home");
        androidPlatformDirOpt.setArgName("sdk platforms");
        options.addOption(androidPlatformDirOpt);

        Option sourceToSinkOpt = new Option("source_sink", true, "path to sources and sinks file");
        sourceToSinkOpt.setArgName("text file");
        options.addOption(sourceToSinkOpt);

        Option jsaOpt = new Option("jsa", true, "enable string analysis during building bridges (default is true)");
        jsaOpt.setArgName("true/false");
        options.addOption(jsaOpt);

        Option ptaOpt = new Option("pta", true, "enable points-to analysis during building bridges (default is true)");
        ptaOpt.setArgName("true/false");
        options.addOption(ptaOpt);

        Option modeOpt = new Option("m", true, modeDescription);
        modeOpt.setArgName("mode string");
        options.addOption(modeOpt);

//        Option quiet = new Option("quiet", "be extra quiet");
//        Option debug = new Option("debug", "print debug information");
//        Option forceAndroidJar = OptionBuilder.withArgName("file").isRequired()
//                .hasArg().withDescription("path to android.jar").create("sdk");
//        Option webDir = OptionBuilder.withArgName("directory")
//                .hasArg().withDescription("path to webpages").create("web");
//        Option outFormat = OptionBuilder.withArgName("jimple or dex")
//                .hasArg().withDescription("output format, default is dex").create('f');

//        options.addOption(quiet);
//        options.addOption(debug);
//        options.addOption(forceAndroidJar);
//        options.addOption(webDir);
//        options.addOption(outFormat);

        CommandLineParser parser = new BasicParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            String optRequiredPrompt = "Option %s is required in [%s] mode.";

            if (cmd.hasOption('m')) Config.mode = cmd.getOptionValue('m');
            if (modeAll.equals(Config.mode) || modeBuildBridge.equals(Config.mode)) {
                if (cmd.hasOption('d')) Config.workingDirPath = cmd.getOptionValue('d');
                else throw new ParseException(String.format(optRequiredPrompt, "d", Config.mode));
                if (cmd.hasOption("i")) Config.appFilePath = cmd.getOptionValue("i");
                else throw new ParseException(String.format(optRequiredPrompt, "i", Config.mode));
                if (cmd.hasOption("sdk")) Config.androidPlatformDir = cmd.getOptionValue("sdk");
                else throw new ParseException(String.format(optRequiredPrompt, "sdk", Config.mode));
                if (cmd.hasOption("source_sink")) Config.sourceAndSinkFilePath = cmd.getOptionValue("source_sink");
                else throw new ParseException(String.format(optRequiredPrompt, "source_sink", Config.mode));

                File appFile = new File(Config.appFilePath);
                if (!appFile.exists()) {
                    throw new ParseException("invalid app file path: " + Config.appFilePath);
                }
                File sdkFile = new File(Config.androidPlatformDir);
                if (!sdkFile.exists()) {
                    throw new ParseException("invalid android platforms path: " + Config.androidPlatformDir);
                }
                File sourceSinkFile = new File(Config.sourceAndSinkFilePath);
                if (!sourceSinkFile.exists()) {
                    throw new ParseException("invalid SourcesAndSinks file path: " + Config.sourceAndSinkFilePath);
                }
                if (cmd.hasOption("jsa")) {
                    String opt = cmd.getOptionValue("jsa");
                    if ("true".equals(opt)) Config.runJSA = true;
                    else if ("false".equals(opt)) Config.runJSA = false;
                    else throw new ParseException("jsa option should be true or false");
                }
                if (cmd.hasOption("pta")) {
                    String opt = cmd.getOptionValue("pta");
                    if ("true".equals(opt)) Config.runPTA = true;
                    else if ("false".equals(opt)) Config.runPTA = false;
                    else throw new ParseException("pta option should be true or false");
                }
            }
            else if (modeRunTaintAnalysis.equals(Config.mode)) {
                if (cmd.hasOption('d')) Config.workingDirPath = cmd.getOptionValue('d');
                else throw new ParseException(String.format(optRequiredPrompt, "d", Config.mode));
                if (cmd.hasOption("sdk")) Config.androidPlatformDir = cmd.getOptionValue("sdk") + "/platforms";
                else throw new ParseException(String.format(optRequiredPrompt, "sdk", Config.mode));
            }
            else if (modeMergeTaintFlow.equals(Config.mode)) {
                if (cmd.hasOption('d')) Config.workingDirPath = cmd.getOptionValue('d');
                else throw new ParseException(String.format(optRequiredPrompt, "d", Config.mode));
            }
            else {
                throw new ParseException("invalid mode: " + Config.mode);
            }
//            if (cmd.hasOption("web")) Config.htmlDirPath = cmd.getOptionValue("web");
//            if (cmd.hasOption('f')) Config.outputFormat = cmd.getOptionValue('f');
//            if (cmd.hasOption("debug")) Util.LOGGER.setLevel(Level.ALL);
//            if (cmd.hasOption("quiet")) Util.LOGGER.setLevel(Level.WARNING);
//            if (!("jimple".equals(Config.outputFormat) || "dex".equals(Config.outputFormat))) {
//                throw new ParseException("output format should be jimple or dex");
//            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(Config.projectName, options);
            return false;
        }

        return true;
    }

    public static void configSoot() {
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_dir(Config.javaDirPath);
        Options.v().set_debug(true);
        Options.v().set_validate(true);
        Options.v().set_output_format(Options.output_format_dex);

        List<String> process_dirs = new ArrayList<>();
        process_dirs.add(Config.appFilePath);
        Options.v().set_process_dir(process_dirs);
        if (!("".equals(Config.androidPlatformDir)))
            Options.v().set_android_jars(Config.androidPlatformDir);
        if (!("".equals(Config.forceAndroidJarPath)))
            Options.v().set_force_android_jar(Config.forceAndroidJarPath);
    }

    public static PrintStream getBridgePs() {
        if (bridgePs == null) {
            Util.LOGGER.log(Level.WARNING, "bridge printer is null, use stdout instead.");
            return System.out;
        }
        return bridgePs;
    }

    public static PrintStream getExceptionLogPs() {
        if (logPs == null) {
            Util.LOGGER.warning("log printer is null, use stdout instead.");
            return System.out;
        }
        return logPs;
    }

    public static boolean setUpFileStructure() {
//        File workingDir = new File(String.format("%s/%s_%s/", Config.workingDirPath,
//                Config.projectName, Util.getTimeString()));
        File workingDir = new File(Config.workingDirPath);
        Config.workingDirPath = workingDir.getPath();

        if (!workingDir.exists() && !workingDir.mkdirs())
            return false;
        try {
            FileUtils.cleanDirectory(workingDir);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        File javaDir = new File(Config.workingDirPath + "/java");
        if (!javaDir.exists() && !javaDir.mkdir())
            return false;
        javaDirPath = javaDir.getPath();

        File htmlDir = new File(Config.workingDirPath + "/html");
        if (!htmlDir.exists() && !htmlDir.mkdir())
            return false;
        htmlDirPath = htmlDir.getPath();

        File bridgeDir = new File(Config.workingDirPath + "/bridge");
        if (!bridgeDir.exists() && !bridgeDir.mkdir())
            return false;
        bridgeDirPath = bridgeDir.getPath();

        File logFile = new File(Config.workingDirPath + "/exception.log");
        File bridgeFile = new File(Config.bridgeDirPath + "/bridge.txt");
        File normalLogFile = new File(Config.workingDirPath + "/analysis.log");
        File sourceSinkFile = new File(Config.workingDirPath + "/SourcesAndSinks.txt");

        try {
            FileUtils.copyFile(new File(Config.sourceAndSinkFilePath), sourceSinkFile);
            bridgePs = new PrintStream(new FileOutputStream(bridgeFile));
            logPs = new PrintStream(new FileOutputStream(logFile));
            FileHandler fh = new FileHandler(normalLogFile.getAbsolutePath());
            fh.setFormatter(new SimpleFormatter());
            Util.LOGGER.addHandler(fh);
            getApkAssets(Config.appFilePath, Config.htmlDirPath);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static void getApkAssets(String apkPath, String destPath) throws IOException {
        ZipFile zipApk = new ZipFile(apkPath);
        Enumeration<? extends ZipEntry> entries = zipApk.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith("assets") && (entryName.endsWith(".html") || entryName.endsWith(".js"))) {
                String tgtPath = String.format("%s/%s", destPath, entryName);
                InputStream in = zipApk.getInputStream(entry);
                File tgtFile = new File(tgtPath);
                tgtFile.getParentFile().mkdirs();
                Files.copy(in, tgtFile.toPath());
            }
        }
    }

    public static HashSet<String> javaSourcesAndSinks = new HashSet<>();
    public static HashSet<String> htmlSourcesAndSinks = new HashSet<>();
    public static boolean readSourceAndSink() {
        File sourceAndSinkFile = new File(Config.sourceAndSinkFilePath);
        try {
            List<String> lines = FileUtils.readLines(sourceAndSinkFile);
            for (String line : lines) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#") || line.startsWith("%")) {
                    continue;
                }
                if (line.startsWith("HTML")) {
                    htmlSourcesAndSinks.add(line);
                }
                else {
                    javaSourcesAndSinks.add(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        String apkFile = "/home/liyc/temp/apk_samples/wacai.apk";
        String dst = "/home/liyc/temp/wacai_assets";
        try {
            getApkAssets(apkFile, dst);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
