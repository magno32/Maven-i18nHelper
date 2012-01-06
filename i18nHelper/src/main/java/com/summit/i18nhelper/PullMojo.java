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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * @parameter expression="i18nHelper.translation"
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
     * @parameter expression=""
     */
    private List<String> languageCodes;

    @Override
    public void execute()
            throws MojoExecutionException {


        File f = outputDirectory;

        if (!f.exists()) {
            f.mkdirs();
        }

        File touch = new File(f, outputFileName);
        FileSetManager fsm = new FileSetManager();
        List<String> filePaths = new ArrayList<String>();
        for (FileSet resourceDir : bundleLocations) {
            filePaths.addAll(Arrays.asList(fsm.getIncludedFiles(resourceDir)));
        }
        
        for(String filePath : filePaths){
            getLog().info("Appending: " + filePath);
        }

        FileWriter w = null;
        try {
            w = new FileWriter(touch);

            w.write(outputFileName);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + touch, e);
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
