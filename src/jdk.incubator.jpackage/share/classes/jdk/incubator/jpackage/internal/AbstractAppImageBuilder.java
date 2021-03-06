/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.incubator.jpackage.internal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ArrayList;

import jdk.incubator.jpackage.internal.resources.ResourceLocator;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

/*
 * AbstractAppImageBuilder
 *     This is sub-classed by each of the platform dependent AppImageBuilder
 * classes, and contains resource processing code common to all platforms.
 */

public abstract class AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MainResources");

    private final Path root;

    public AbstractAppImageBuilder(Map<String, Object> unused, Path root) {
        this.root = root;
    }

    public InputStream getResourceAsStream(String name) {
        return ResourceLocator.class.getResourceAsStream(name);
    }

    public abstract void prepareApplicationFiles(
            Map<String, ? super Object> params) throws IOException;
    public abstract void prepareJreFiles(
            Map<String, ? super Object> params) throws IOException;
    public abstract Path getAppDir();
    public abstract Path getAppModsDir();

    public Path getRuntimeRoot() {
        return this.root;
    }

    protected void copyEntry(Path appDir, File srcdir, String fname)
            throws IOException {
        Path dest = appDir.resolve(fname);
        Files.createDirectories(dest.getParent());
        File src = new File(srcdir, fname);
        if (src.isDirectory()) {
            IOUtils.copyRecursive(src.toPath(), dest);
        } else {
            Files.copy(src.toPath(), dest);
        }
    }

    public void writeCfgFile(Map<String, ? super Object> params,
            File cfgFileName) throws IOException {
        cfgFileName.getParentFile().mkdirs();
        cfgFileName.delete();
        File mainJar = JLinkBundlerHelper.getMainJar(params);
        ModFile.ModType mainJarType = ModFile.ModType.Unknown;

        if (mainJar != null) {
            mainJarType = new ModFile(mainJar).getModType();
        }

        String mainModule = StandardBundlerParam.MODULE.fetchFrom(params);

        try (PrintStream out = new PrintStream(cfgFileName)) {

            out.println("[Application]");
            out.println("app.name=" + APP_NAME.fetchFrom(params));
            out.println("app.version=" + VERSION.fetchFrom(params));
            out.println("app.runtime=" + getCfgRuntimeDir());
            out.println("app.identifier=" + IDENTIFIER.fetchFrom(params));
            out.println("app.classpath="
                    + getCfgClassPath(CLASSPATH.fetchFrom(params)));

            // The main app is required to be a jar, modular or unnamed.
            if (mainModule != null &&
                    (mainJarType == ModFile.ModType.Unknown ||
                    mainJarType == ModFile.ModType.ModularJar)) {
                out.println("app.mainmodule=" + mainModule);
            } else {
                String mainClass =
                        StandardBundlerParam.MAIN_CLASS.fetchFrom(params);
                // If the app is contained in an unnamed jar then launch it the
                // legacy way and the main class string must be
                // of the format com/foo/Main
                if (mainJar != null) {
                    out.println("app.mainjar=" + getCfgAppDir()
                            + mainJar.toPath().getFileName().toString());
                }
                if (mainClass != null) {
                    out.println("app.mainclass="
                            + mainClass.replace("\\", "/"));
                }
            }

            out.println();
            out.println("[JavaOptions]");
            List<String> jvmargs = JAVA_OPTIONS.fetchFrom(params);
            for (String arg : jvmargs) {
                out.println(arg);
            }
            Path modsDir = getAppModsDir();

            if (modsDir != null && modsDir.toFile().exists()) {
                out.println("--module-path");
                out.println(getCfgAppDir().replace("\\","/") + "mods");
            }

            out.println();
            out.println("[ArgOptions]");
            List<String> args = ARGUMENTS.fetchFrom(params);
            for (String arg : args) {
                if (arg.endsWith("=") &&
                        (arg.indexOf("=") == arg.lastIndexOf("="))) {
                    out.print(arg.substring(0, arg.length() - 1));
                    out.println("\\=");
                } else {
                    out.println(arg);
                }
            }
        }
    }

    File getRuntimeImageDir(File runtimeImageTop) {
        return runtimeImageTop;
    }

    protected String getCfgAppDir() {
        return "$ROOTDIR" + File.separator
                + getAppDir().getFileName() + File.separator;
    }

    protected String getCfgRuntimeDir() {
        return "$ROOTDIR" + File.separator + "runtime";
    }

    String getCfgClassPath(String classpath) {
        String cfgAppDir = getCfgAppDir();

        StringBuilder sb = new StringBuilder();
        for (String path : classpath.split("[:;]")) {
            if (path.length() > 0) {
                sb.append(cfgAppDir);
                sb.append(path);
                sb.append(File.pathSeparator);
            }
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
