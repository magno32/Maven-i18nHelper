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
package com.summit.i18nhelper;

import com.google.api.GoogleAPI;
import com.google.api.GoogleAPIException;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 *
 * This goal uses the Google Translation API to translate a document generated from
 * the pull goal.
 *
 * @goal translate
 * @requiresOnline true
 * @phase process-sources
 * 
 * @author justin
 */
public class GoogleTranslateMojo extends AbstractMojo {

    /**
     * Location of the file.
     * @parameter 
     *  expression="${project.build.directory}" 
     *  default-value="${project.build.directory}"
     */
    private File outputDirectory;
    /**
     * Output File name.
     * @parameter 
     *  expression="${i18nHelper.translateOut}" 
     *  default-value="i18nHelper-translated.properties"
     */
    private String outputFileName;
    /**
     * The generated file from the pull goal.
     * @parameter 
     *  expression="${i18nHelper.translateIn}"
     *  default-value="${project.build.directory}/i18nHelper.properties"
     */
    private File fileToTranslate;
    /**
     * What is the base locale in your resource bundles
     * 
     * @parameter 
     *  default-value="en"
     */
    private String defaultLocale;
    /**
     * Force the translation even if one is provided
     * @parameter 
     *  expression="${i18nHelper.forceTranslate}"
     *  default-value="false"
     */
    private boolean forceTranslation;
    /**
     * Languages codes to include in the pull, these will be added to the generated file.
     * 
     * File output will be a java properties file
     * 
     * @parameter 
     */
    private List<String> languageCodes;
    
    /**
     * 
     * @parameter
     */
    private String languageCode;
    
    /**
     * Google API Key, you have to provide this yourself...
     * 
     * @parameter expression="${translate.googleApiKey}"
     * 
     */
    private String googleApiKey;
    /**
     * Number of worker threads to use.
     * 
     * @parameter 
     *  expression="${translate.workers}"
     *  default-value="10"
     */
    private int workers;
    /**
     * Referrer for the call to google's api.
     * 
     * @parameter 
     *  expression="${translate.referrer}" 
     *  default-value="http://www.summitsystemsinc.com"
     */
    private URL httpReferrer;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        BufferedReader in = null;
        OutputStreamWriter out = null;

        final List<Future> runningTasks = new LinkedList<Future>();
        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        if(languageCodes == null){
            languageCodes = Collections.EMPTY_LIST;
        }

        if(languageCode != null && !languageCode.isEmpty()){
            languageCodes = Arrays.asList(languageCode);
        }
        
        File f = outputDirectory;
        if (!f.exists()) {
            f.mkdirs();
        }
        File outFile = new File(f, outputFileName);
        for (String lc : languageCodes) {
            getLog().info("Using: " + lc);
        }
        try {

            final Properties fullFile = new Properties();
            fullFile.load(new InputStreamReader(new FileInputStream(fileToTranslate), Charset.forName("UTF-8")));

            GoogleAPI.setHttpReferrer(httpReferrer.toString());
            getLog().info("Using api key: " + googleApiKey);
            GoogleAPI.setKey(googleApiKey);

            int totalKeys = fullFile.size();
            int currentKeyNum = 0;
            for (final String key : fullFile.stringPropertyNames()) {
                runningTasks.add(executorService.submit(new Runnable() {

                    @Override
                    public void run() {
                        int pointLocation = key.lastIndexOf('.');

                        if (pointLocation > 0) {
                            String possibleCode = key.substring(pointLocation + 1);
                            String baseKey = key.substring(0, pointLocation);
                            if (languageCodes.contains(possibleCode)) {
                                String localized = fullFile.getProperty(key);
                                if (localized == null || localized.trim().length() == 0 || forceTranslation) {
                                    String baseValue = fullFile.getProperty(baseKey);
                                    getLog().debug("Translating \"" + baseValue + "\" to " + possibleCode);
                                    //String translation = GoogleTranslate.translate(baseValue, defaultLocale, possibleCode, googleApiKey);
                                    try {
                                        String translation = Translate.DEFAULT.execute(baseValue, Language.fromString(defaultLocale), Language.fromString(possibleCode));
                                        //TODO i don't know enough about unicode to make this prettier
                                        translation = nativeToAscii(translation);
                                        fullFile.setProperty(key, translation);
                                        getLog().debug("Translated \"" + baseValue + "\" to \"" + translation + "\" (" + possibleCode + ")");
                                    } catch (GoogleAPIException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                            }
                        }
                    }
                }));
            }

            //wait for these to finish...
            for (Future task : runningTasks) {
                currentKeyNum++;
                try {
                    task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    throw new MojoExecutionException(ex.getCause(), ex.getCause().getMessage(), "Error doing translation.");
                }
                if (currentKeyNum % 10 == 0) {
                    getLog().info(NumberFormat.getPercentInstance().format(currentKeyNum / (double) totalKeys));
                }
            }

            in = new BufferedReader(new InputStreamReader(new FileInputStream(fileToTranslate), Charset.forName("UTF-8")));
            out = new OutputStreamWriter(new FileOutputStream(outFile), Charset.forName("UTF-8"));
            getLog().debug(fileToTranslate.getPath());



            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) {
                    out.write(line + "\r\n");
                    continue;
                }
                if (line.trim().length() == 0) {
                    out.write(line + "\r\n");
                    continue;
                }

                int splitLocation = line.indexOf("=");
                if (splitLocation > 0) {
                    String splitLine[] = line.split("=");
                    final String propertyName = splitLine[0];
                    out.write(propertyName);
                    out.write("=");
                    out.write(fullFile.getProperty(propertyName));
                    out.write("\r\n");
                } else {
                    //there was no "=" so just write the line back out... Garbage in Garbage out...
                    out.write(line + "\r\n");
                    continue;
                }

            }
        } catch (FileNotFoundException ex) {
            throw new MojoFailureException(ex, ex.getLocalizedMessage(), fileToTranslate + " was not found.");
        } catch (IOException ex) {
            throw new MojoExecutionException(ex, ex.getLocalizedMessage(), "Error processing the input file.");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (Exception ex) {
            }
        }
    }

    /**
     * Converts given CharSequence into ASCII String.
     */
    public static String nativeToAscii(CharSequence cs) {
        if (cs == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (c <= 0x7E) {
                sb.append(c);
            } else {
                sb.append("\\u");
                String hex = Integer.toHexString(c);
                for (int j = hex.length(); j < 4; j++) {
                    sb.append('0');
                }
                sb.append(hex);
            }
        }
        return sb.toString();
    }
}
