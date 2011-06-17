package edu.berkeley.nlp.lm.map;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;

import edu.berkeley.nlp.lm.array.LongArray;
import edu.berkeley.nlp.lm.collections.Iterators;
import edu.berkeley.nlp.lm.util.Annotations.PrintMemoryCount;
import edu.berkeley.nlp.lm.util.Logger;
import edu.berkeley.nlp.lm.util.MurmurHash;

/**
 * Low-level hash map which stored context-encoded parent pointers in a trie.
 * 
 * @author adampauls
 * 
 */
final class ImplicitWordHashMap implements Serializable, HashMap
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@PrintMemoryCount
	private final LongArray keys;

	@PrintMemoryCount
	private final long[] wordRanges;

	private long numFilled = 0;

	private static final int EMPTY_KEY = -1;

	public ImplicitWordHashMap(final LongArray numNgramsForEachWord, final double loadFactor) {
		final long numWords = numNgramsForEachWord.size();
		wordRanges = new long[(int) numWords];
		final long totalNumNgrams = setWordRanges(numNgramsForEachWord, loadFactor, numWords);
		keys = LongArray.StaticMethods.newLongArray(totalNumNgrams, totalNumNgrams, totalNumNgrams);
		Logger.logss("No word key size " + totalNumNgrams);
		keys.fill(EMPTY_KEY, totalNumNgrams);
		numFilled = 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.lm.map.HashMap#put(long)
	 */
	@Override
	public long put(final long key) {
		long i = linearSearch(key, true);
		if (keys.get(i) == EMPTY_KEY) numFilled++;
		setKey(i, key);

		return i;
	}

	/**
	 * @param numNgramsForEachWord
	 * @param maxLoadFactor
	 * @param numWords
	 * @return
	 */
	private long setWordRanges(final LongArray numNgramsForEachWord, final double maxLoadFactor, final long numWords) {
		long currStart = 0;
		for (int w = 0; w < numWords; ++w) {
			wordRanges[w] = currStart;
			final long numNgrams = numNgramsForEachWord.get(w);
			currStart += numNgrams <= 3 ? numNgrams : Math.round(numNgrams * 1.0 / maxLoadFactor);

		}
		return currStart;
	}

	private void setKey(final long index, final long putKey) {
		final long contextOffset = AbstractNgramMap.contextOffsetOf(putKey);
		assert contextOffset >= 0;
		keys.set(index, contextOffset);

	}

	public final long getOffset(final long key) {
		return linearSearch(key, false);
	}

	/**
	 * @param key
	 * @param returnFirstEmptyIndex
	 * @return
	 */
	private long linearSearch(final long key, boolean returnFirstEmptyIndex) {
		int word = AbstractNgramMap.wordOf(key);
		if (word >= wordRanges.length) return -1;
		final long rangeStart = wordRanges[word];
		final long rangeEnd = ((word == wordRanges.length - 1) ? getCapacity() : wordRanges[word + 1]);
		final long startIndex = hash(key, rangeStart, rangeEnd);
		if (startIndex < 0) return -1L;
		assert startIndex >= rangeStart;
		assert startIndex < rangeEnd;
		return keys.linearSearch(AbstractNgramMap.contextOffsetOf(key), rangeStart, rangeEnd, startIndex, EMPTY_KEY, returnFirstEmptyIndex);
	}

	public long getCapacity() {
		return keys.size();
	}

	public double getLoadFactor() {
		return (double) numFilled / getCapacity();
	}

	private long hash(final long key, final long startOfRange, final long endOfRange) {
		final long numHashPositions = endOfRange - startOfRange;
		if (numHashPositions == 0) return -1;
		long hash = (MurmurHash.hashOneLong(key, 31));
		if (hash < 0) hash = -hash;
		hash = (hash % numHashPositions);
		return hash + startOfRange;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.lm.map.HashMap#getNextOffset(long)
	 */
	long getNextOffset(long offset) {
		return keys.get(offset);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.lm.map.HashMap#getWordForContext(long)
	 */
	int getWordForContext(long contextOffset) {
		int binarySearch = Arrays.binarySearch(wordRanges, contextOffset);
		binarySearch = binarySearch >= 0 ? binarySearch : (-binarySearch - 2);
		while (binarySearch < wordRanges.length - 1 && wordRanges[binarySearch] == wordRanges[binarySearch + 1])
			binarySearch++;
		return binarySearch;
	}

	@Override
	public long getKey(long contextOffset) {
		return AbstractNgramMap.combineToKey(getWordForContext(contextOffset), getNextOffset(contextOffset));
	}

	@Override
	public boolean isEmptyKey(long key) {
		return key == EMPTY_KEY;
	}

	@Override
	public long size() {
		return numFilled;
	}

	@Override
	public Iterable<Long> keys() {
		return Iterators.able(new KeyIterator(keys));
	}

}