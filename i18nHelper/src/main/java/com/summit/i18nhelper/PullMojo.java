package com.summit.i18nhelper;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * Goal that generates a single properties file from i18n properties bundles.
 *
 * @goal pull
 * 
 * @phase process-sources
 */
public class PullMojo
        extends AbstractMojo {

    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;
    /**
     * Output File name.
     * @parameter expression="i18nHelper.properties"
     * @required
     */
    private String outputFileName;
    /**
     * Places to look for bundles
     * @parameter
     * @required 
     */
    private FileSet[] bundleLocations;
    /**
     * Languages codes to include in the pull, these will be added to the generated file.
     * 
     * File output will be a java properties file
     * 
     * @parameter expression=""
     */
    private List<String> languageCodes;

    @Override
    public void execute()
            throws MojoExecutionException {
        if(languageCodes == null){
            languageCodes = Collections.EMPTY_LIST;
        }else{
            Collections.sort(languageCodes);
        }
        
        File f = outputDirectory;
        if (!f.exists()) {
            f.mkdirs();
        }

        File outFile = new File(f, outputFileName);
        FileWriter w = null;
        try {
            w = new FileWriter(outFile);

            FileSetManager fsm = new FileSetManager();

            for (FileSet resourceDir : bundleLocations) {
                String dir = resourceDir.getDirectory();
                for (String file : fsm.getIncludedFiles(resourceDir)) {
                    String fullPath = dir + "/" + file;

                    getLog().info(fullPath);

                    w.write("#" + file + "\r\n");
                    Properties defaults = new Properties();
                    Map<String, Properties> localizedFiles = new HashMap<String, Properties>();
                    for (String locale : languageCodes) {
                        Properties localizedProperties = new Properties();
                        File localizedFile = new File(fullPath.replaceAll(".properties", "_" + locale + ".properties"));
                        if (localizedFile.exists()) {
                            localizedProperties.load(new FileInputStream(localizedFile));
                            localizedFiles.put(locale, localizedProperties);
                        }
                    }

                    defaults.load(new FileInputStream(new File(fullPath)));
                    for (String prop : defaults.stringPropertyNames()) {
                        w.write(prop + "=" + defaults.getProperty(prop) + "\r\n");
                        for (String locale : languageCodes) {
                            String localeProp = prop + "." + locale;
                            String translation = "";
                            if (localizedFiles.get(locale) != null) {
                                translation = localizedFiles.get(locale).getProperty(prop,"");
                            }
                            w.write(localeProp + "=" + translation + "\r\n");
                        }
                        w.write("\r\n");
                    }
                }
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + outFile, e);
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
