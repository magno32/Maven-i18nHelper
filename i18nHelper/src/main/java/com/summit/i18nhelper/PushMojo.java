/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.summit.i18nhelper;

import java.io.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Goal that generates a single properties file from i18n properties bundles.
 *
 * @goal push
 *
 * @phase generate-resources
 *
 * @author Justin Smith
 */
public class PushMojo extends AbstractMojo {

    /**
     * Location of the file.
     *
     * @parameter expression="${i18nHelper.resource.directory}"
     * default-value="${project.build.directory}/classes/"
     *
     * @required
     */
    private File outputDirectory;
    /**
     * @parameter expression="${i18nHelper.translated}"
     */
    private File inputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!outputDirectory.exists()) {
            getLog().debug("Creating the output resource directory.");
            outputDirectory.mkdirs();
        }

        if (inputFile == null) {
            throw new MojoExecutionException("inputFile cannot be null.");
        }

        try {
            BufferedReader inputFileReader = new BufferedReader(new FileReader(inputFile));
            String line = inputFileReader.readLine();

            String key = null;

            String currentWritingDir = null;
            //This is just the file base name, will need to append _${local}.properties
            String currentWritingFile = null;

            while (line != null) {
                getLog().debug("Read: " + line);
                if (line.equals("")) {
                    getLog().debug("Empty Line, Resetting current key.");
                    key = null;
                } else {
                    if (line.startsWith("#")) {
                        File outFile = new File(outputDirectory, line.substring(1));

                        String filePath = outFile.getPath();
                        currentWritingDir = filePath.substring(0, filePath.lastIndexOf('/'));
                        File outDir = new File(currentWritingDir);
                        if (!outDir.exists()) {
                            outDir.mkdirs();
                        }
                        currentWritingFile = filePath.substring(filePath.lastIndexOf('/') + 1, filePath.indexOf(".properties"));
                        getLog().debug("Current directory: " + currentWritingDir);
                        getLog().debug("Current fileBase: " + currentWritingFile);

                    } else {
                        if (key == null) {
                            key = line.substring(0, line.indexOf('='));
                            getLog().debug("Current Key: " + key);
                        } else {
                            String localeCode = line.substring(key.length() + 1, line.indexOf('='));
                            getLog().debug("Locale: " + localeCode);
                            String valueToWrite = line.substring(line.indexOf('=') + 1);
                            File outFile = new File(currentWritingDir + "/" + currentWritingFile + "_" + localeCode + ".properties");
                            String stringToWrite = key + "=" + valueToWrite;
                            getLog().debug("Writing: \"" + stringToWrite + "\" to " + outFile.getPath());
                            BufferedWriter outWriter = new BufferedWriter(new FileWriter(outFile, true));
                            outWriter.write(stringToWrite + "\r\n");
                            outWriter.close();
                        }
                    }
                }
                line = inputFileReader.readLine();
            }

            inputFileReader.close();
        } catch (FileNotFoundException ex) {
            throw new MojoFailureException(ex.getMessage());
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage());
        }
    }
}
