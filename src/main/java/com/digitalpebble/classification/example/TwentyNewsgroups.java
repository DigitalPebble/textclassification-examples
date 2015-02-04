/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpebble.classification.example;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.util.Version;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Field;
import com.digitalpebble.classification.FileTrainingCorpus;
import com.digitalpebble.classification.Learner;
import com.digitalpebble.classification.Parameters;

/**
 * Generates a vector file for the 20 newsgroup. Useful to demonstrate how to
 * use the API.
 */
public final class TwentyNewsgroups {

	FileTrainingCorpus filetrainingcorpus = null;
	Learner learner = null;
	Parser tikaParser = null;
	Analyzer analyzer = null;

	private TwentyNewsgroups(File parentDir, File outputDir) throws Exception {
		// use Tika-0.8 to parse the mail files and generate a raw file
		// we can specify the right parser type

		MediaType mimeType = MediaType.parse("message/rfc822");

		tikaParser = TikaConfig.getDefaultConfig().getParser(mimeType);

		String outpath = outputDir.getAbsolutePath().toString();
		learner = Learner.getLearner(outpath, Learner.LibSVMModelCreator, true);
		filetrainingcorpus = learner.getFileTrainingCorpus();
		learner.setMethod(Parameters.WeightingMethod.TFIDF);
		String analyzerName = "org.apache.lucene.analysis.standard.StandardAnalyzer";
		try {
			analyzer = (Analyzer) Class.forName(analyzerName).newInstance();
		} catch (InstantiationException e) {
			analyzer = (Analyzer) Class.forName(analyzerName)
					.getConstructor(Version.class)
					.newInstance(Version.LUCENE_30);
		}

		recursiveParsing(parentDir, null);
		filetrainingcorpus.close();
        learner.saveLexicon();
	}

	private void recursiveParsing(File fileordir, String label) {
		if (fileordir.isDirectory()) {
			// get the name of the directory and use it as a label
			File[] files = fileordir.listFiles();
			for (File f : files)
				recursiveParsing(f, fileordir.getName());
		} else {
			try {
				addDocument(fileordir, label);
			} catch (Exception e) {
				// dumps the trace and resume
				e.printStackTrace();
			}
		}
	}

	private void addDocument(File file, String label) throws Exception {
		Metadata md = new Metadata();
		MailHandler mh = new MailHandler();
		ContentHandler handler = new BodyContentHandler(mh);
		tikaParser.parse(file.toURL().openStream(), handler, md,
				new ParseContext());
		// get the metadata + main text
		ArrayList<Field> fields = new ArrayList<Field>();
		String subj = md.get(Metadata.SUBJECT);
		String summary = md.get("RFC822-Summary");
		String keywords = md.get(Metadata.KEYWORDS);
		String content = mh.getString();

		List<String> tokens = analyseField(subj);
		if (tokens != null) {
			Field f = new Field("subject", tokens);
			fields.add(f);
		}
		tokens = analyseField(summary);
		if (tokens != null) {
			Field f = new Field("summary", tokens);
			fields.add(f);
		}
		tokens = analyseField(keywords);
		if (tokens != null) {
			Field f = new Field("keywords", tokens);
			fields.add(f);
		}
		tokens = analyseField(content);
		if (tokens != null) {
			Field f = new Field("content", tokens);
			fields.add(f);
		}
		Document doc = learner.createDocument(fields, label);
		filetrainingcorpus.addDocument(doc);
	}

	private List<String> analyseField(String content) throws IOException {
		if (content == null)
			return null;
		List<String> tokens = new ArrayList<String>();
		StringReader sr = new StringReader(content);
		TokenStream ts = analyzer.tokenStream("dummyValue", sr);
		TermAttribute term = ts.addAttribute(TermAttribute.class);
		while (ts.incrementToken()) {
			tokens.add(term.term());
		}
		return tokens;
	}

	public static void main(String[] args) throws Exception {
		File parentDir = new File(args[0]);
		File outputDir = new File(args[1]);
		new TwentyNewsgroups(parentDir, outputDir);
	}
}

/**
 * Get the main text of the emails from SAX TODO replace with Tika equivalent
 **/
class MailHandler extends DefaultHandler {
	StringBuffer currentString = new StringBuffer();

	public void clear() {
		currentString = new StringBuffer();
	}

	public String getString() {
		return currentString.toString();
	}

	public void characters(char[] str, int start, int length) {
		currentString.append(str, start, length);
	}
}
