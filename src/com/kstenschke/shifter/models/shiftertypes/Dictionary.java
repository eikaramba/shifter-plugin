/*
 * Copyright 2011-2014 Kay Stenschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kstenschke.shifter.models.shiftertypes;

import com.kstenschke.shifter.models.ShifterPreferences;
import com.kstenschke.shifter.utils.UtilsTextual;
import com.kstenschke.shifter.resources.forms.PluginConfiguration;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dictionary {

		// Set during extension specific detection of dictionary term
	private String fileExtension;

		// Terms-list containing the term to be shifted, set during detection
	private String relevantTermsList;

		// The complete dictionary
	private final String dictionaryContents;

	/**
	 * Constructor
	 */
	public Dictionary() {
		String contents	= ShifterPreferences.getDictionary();
		if( contents.isEmpty() ) {
			contents	= new PluginConfiguration().getDefaultDictionary();
		}

		this.dictionaryContents = contents;
	}

	/**
	 * Check whether the given term exists in any section of shift-lists of the dictionary
	 * + Stores matching line containing the term for use in shifting later
	 *
	 * @param	term		String to be looked for in shifter dictionary
	 * @return	boolean
	 */
	public boolean isTermInDictionary(String term, boolean isFileExtensionRelevant) {
		if( ! isFileExtensionRelevant || this.dictionaryContents.contains("|" + this.fileExtension + "|")) {
				// Merge all terms-blocks
			String dictionaryTerms              = this.dictionaryContents;
			Object[] dictionaryExtensionsBlocks = this.getAllFileExtensionsBlockStarts();

			for(Object dictionaryExtensionsBlock : dictionaryExtensionsBlocks) {
				String currentExtensionsList    = dictionaryExtensionsBlock.toString();
				dictionaryTerms                 = dictionaryTerms.replace(currentExtensionsList, "");
			}

				// Term is contained? store list of shifting neighbours
			if( dictionaryTerms.contains("|" + term + "|") ) {
				this.relevantTermsList	= extractFirstMatchingTermsLine(dictionaryTerms, term);
				return true;
			}
                // Not found case-sensitive, try insensitive
            String dictionaryTermsLower = dictionaryTerms.toLowerCase();
            String termLower            = term.toLowerCase();
            if( dictionaryTermsLower.contains("|" + termLower + "|") ) {
                this.relevantTermsList	= extractFirstMatchingTermsLine(dictionaryTermsLower, termLower);
                return true;
            }
		}

		return false;
	}

	/**
	 * Check whether the given term exists in any section of shift-lists of the dictionary,
	 * looking only at lists in blocks having assigned the given extension
	 * + Stores first matching line containing the term for use in shifting later
	 *
	 * @param	term			String to be looked for in shifter dictionary
	 * @param	fileExtension	Extension of edited file
	 * @return	boolean
	 */
	public boolean isTermInDictionary(String term, String fileExtension) {
		if( fileExtension != null && this.dictionaryContents.contains("|" + fileExtension + "|")) {
			this.fileExtension	= fileExtension;

				// Reduce to first term-list of terms-block(s) of the given file extension, containing the given term
			Object[] blocksOfExtension	= getAllFileExtensionsBlockStarts(fileExtension);

				// Go over all blocks of lists of shift-terms, fetch first one containing the term
			for (Object aBlocksOfExtension : blocksOfExtension) {
				String curExtsList = aBlocksOfExtension.toString();
				String curShiftTermsBlock = StringUtils.substringBetween(
						this.dictionaryContents, curExtsList, "}");

					// Term is contained? store list of shifting neighbours
				if (curShiftTermsBlock.contains("|" + term + "|")) {
					this.relevantTermsList = extractFirstMatchingTermsLine(curShiftTermsBlock, term);
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * @param	termsLines	Terms lines from dictionary
	 * @param	term		Word to be shifted
	 * @return	First matching term line
	 */
	private static String extractFirstMatchingTermsLine(String termsLines, String term) {
		String sword		= "|" + term + "|";
		String[] allLines	= termsLines.split("\n");
		int numLines		= allLines.length;

		String curLine;
		int i = 0;

		while(i < numLines) {
			curLine	= allLines[i];
			curLine	= curLine.replaceAll("\\s*", "").replaceAll("\\{*", "").replaceAll("\\}*", "").trim();

			if( ! curLine.isEmpty() && curLine.contains(sword) ) {
				return curLine;
			}

			i++;
		}

		return null;
	}

	/**
	 * Get all starting lines of term-blocks (extensions) from dictionary
	 *
	 * @return	Object[]	e.g. [0 => "('js') {", 1 => "('html') {", 2 => ...]
	 */
	private Object[] getAllFileExtensionsBlockStarts() {
		List<String> allMatches = new ArrayList<String>();

		String pattern	= "\\(\\|([a-z|\\*]+\\|)*\\)(\\s)*\\{";
		Matcher m = Pattern.compile(pattern).matcher(this.dictionaryContents);
		while (m.find()) {
			allMatches.add(m.group());
		}

		return allMatches.toArray();
	}

	/**
	 * Get all starting lines of term-blocks (extensions) from dictionary,
	 * limited to those containing the given file extension
	 *
	 * @return	Object[]
	 */
	private Object[] getAllFileExtensionsBlockStarts(String fileExtension) {
		List<String> allMatches = new ArrayList<String>();
		Object[] dictionaryExtensionsBlocks = this.getAllFileExtensionsBlockStarts();

		for (Object dictionaryExtensionsBlock : dictionaryExtensionsBlocks) {
			String curBlock = dictionaryExtensionsBlock.toString();
			if (curBlock.contains("|" + fileExtension + "|")) {
				allMatches.add(curBlock);
			}
		}

		return allMatches.toArray();
	}

	/**
	 * Shift given word, using the (already fetched) list of relevant terms
	 *
	 * @param	word				Word to be shifted
	 * @param	isUp				Shifting up? (or down)
	 * @return	The shifted word
	 */
	public String getShifted(String word, boolean isUp) {
		String shiftTerms	= this.relevantTermsList.replaceFirst("\\|", "");
			   shiftTerms	= UtilsTextual.replaceLast(shiftTerms, "|", "");

		String[] termsList	= shiftTerms.split("\\|");
		if( termsList.length > 0 ) {
			StaticWordType wordType = new StaticWordType(termsList);
			return wordType.getShifted(word, isUp);
		}

		return word;
	}

}
