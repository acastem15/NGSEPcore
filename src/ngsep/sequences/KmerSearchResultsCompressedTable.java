package ngsep.sequences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KmerSearchResultsCompressedTable {

	//Structures to implement the compressed hits indexed by subjectIdx 
	//Map with subjectIDx as key and row of the sequencesBySubjectTable as value
	private Map<Integer,Integer> matrixRowMap;
	//Table with encoded entries. Each row corresponds to a subject sequence
	private long [][] sequencesBySubjectTable;
	//Actual lengths of the lists within the table
	private int [] sequencesBySubjectTableColumnLengths;
	private int totalHits = 0;
	
	private Map<Integer, Long> searchCodes;
	private Map<Long, Double> kmerWeights;
	
	public KmerSearchResultsCompressedTable(Map<Integer, Long> searchCodes, int capacity) {
		this.searchCodes = searchCodes;
		initializeTable(capacity);
	}
	private void initializeTable(int capacity) {
		matrixRowMap = new HashMap<Integer, Integer>(capacity);
		sequencesBySubjectTable = new long [capacity][1];
		for(int i=0;i<sequencesBySubjectTable.length;i++) Arrays.fill(sequencesBySubjectTable[i], 0);
		sequencesBySubjectTableColumnLengths = new int [capacity];
		Arrays.fill(sequencesBySubjectTableColumnLengths, 0);
	}
	
	private void resizeTable() {
		int newCapacity =  2*sequencesBySubjectTable.length;
		if(newCapacity<0) newCapacity = Integer.MAX_VALUE;
		sequencesBySubjectTableColumnLengths = Arrays.copyOf(sequencesBySubjectTableColumnLengths, newCapacity);
		long [][] newTable = new long [newCapacity][0];
		for(int i=0;i<newCapacity;i++) {
			if(i<sequencesBySubjectTable.length) newTable[i] = sequencesBySubjectTable[i];
			else newTable[i] = new long[1];
		}
		sequencesBySubjectTable = newTable;
	}
	private void addToTable(int row, long value) {
		if(row==sequencesBySubjectTable.length) resizeTable();
		long [] codeEntries = sequencesBySubjectTable[row];
		int column = sequencesBySubjectTableColumnLengths[row];
		if(column == codeEntries.length ) {
			//Resize entries
			sequencesBySubjectTable[row] = Arrays.copyOf(codeEntries, 2*codeEntries.length);
		}
		sequencesBySubjectTable[row][column] = value;
		sequencesBySubjectTableColumnLengths[row]++;
		totalHits++;
	}
	
	public void addKmerHit(int queryStart, int subjectIdx, int subjectStart) {
		int row = matrixRowMap.computeIfAbsent(subjectIdx, v-> matrixRowMap.size());
		addToTable(row, KmerCodesTableEntry.encode(queryStart, subjectStart));
	}
	
	
	public int getTotalHits() {
		return totalHits;
	}
	
	public int getKmerHitCount(int subjectIdx) {
		Integer row = matrixRowMap.get(subjectIdx);
		if(row==null) return 0;
		return sequencesBySubjectTableColumnLengths[row];
	}
	public Map<Integer,Integer> getKmerHitCountsBySubjectId() {
		Map<Integer,Integer> counts = new HashMap<>();
		for(int subjectIdx:matrixRowMap.keySet()) {
			counts.put(subjectIdx, sequencesBySubjectTableColumnLengths[matrixRowMap.get(subjectIdx)]);
		}
		return counts;
	}
	public int countDistinctKmerHits(int subjectIdx) {
		Integer row = matrixRowMap.get(subjectIdx);
		if(row==null) return 0;
		int n = sequencesBySubjectTableColumnLengths[row];
		Set<Long> kmers = new HashSet<>(n);
		long [] encodedHits = sequencesBySubjectTable[row];
		for(int i=0;i<encodedHits.length;i++) {
			int [] dec = KmerCodesTableEntry.decode(encodedHits[i]);
			int queryStart = dec[0];
			Long code = searchCodes.get(queryStart);
			if(code!=null) kmers.add(code);
		}
		return kmers.size();
	}
	public List<UngappedSearchHit> getHits(int subjectIdx, int kmerLength) {
		Integer row = matrixRowMap.get(subjectIdx);
		if(row==null) return new ArrayList<>();
		int n = sequencesBySubjectTableColumnLengths[row];
		List<UngappedSearchHit> hits = new ArrayList<>(n);
		long [] encodedHits = sequencesBySubjectTable[row];
		for(int i=0;i<encodedHits.length;i++) {
			int [] dec = KmerCodesTableEntry.decode(encodedHits[i]);
			int queryStart = dec[0];
			int subjectStart = dec[1];
			UngappedSearchHit hit = new UngappedSearchHit(subjectIdx, subjectStart);
			hit.setHitLength((short)kmerLength);
			hit.setQueryStart(queryStart);
			Long code = searchCodes.get(queryStart);
			if(code!=null) {
				Double weight = kmerWeights.get(code);
				if(weight!=null) hit.setWeight(weight);
				hits.add(hit);	
			}
		}
		return hits;
	}
	public Map<Long, Double> getKmerWeights() {
		return kmerWeights;
	}
	public void setKmerWeights(Map<Long, Double> kmerWeights) {
		this.kmerWeights = kmerWeights;
	}

	
}
