/*******************************************************************************
 * NGSEP - Next Generation Sequencing Experience Platform
 * Copyright 2016 Jorge Duitama
 *
 * This file is part of NGSEP.
 *
 *     NGSEP is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     NGSEP is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with NGSEP.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package ngsep.assembly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import ngsep.alignments.ReadAlignment;
import ngsep.alignments.ReadsAligner;
import ngsep.genome.GenomicRegion;
import ngsep.genome.GenomicRegionPositionComparator;
import ngsep.genome.ReferenceGenome;
import ngsep.main.ThreadPoolManager;
import ngsep.sequences.DNASequence;
import ngsep.sequences.QualifiedSequence;
import ngsep.sequences.QualifiedSequenceList;
import ngsep.sequences.RawRead;
import ngsep.variants.CalledGenomicVariant;
import ngsep.variants.GenomicVariant;

public class AlignmentBasedIndelErrorsCorrector {
	
	private Logger log = Logger.getAnonymousLogger();
	private LayoutBuilderKruskalPath pathsFinder;
	private AssemblySequencesRelationshipFilter filter;
	private int numThreads=1;
	private static Runtime runtime = Runtime.getRuntime();
	
	
	
	public AlignmentBasedIndelErrorsCorrector() {
		super();
		pathsFinder = new LayoutBuilderKruskalPath();
		filter = new AssemblySequencesRelationshipFilter();	
	}

	public Logger getLog() {
		return log;
	}

	public void setLog(Logger log) {
		this.log = log;
		pathsFinder.setLog(log);
	}

	/**
	 * @return Number of threads
	 */
	public int getNumThreads() {
		return numThreads;
	}

	/**
	 * Changes the number of threads
	 * @param numThreads
	 */
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
	/**
	 * Corrects errors in reads
	 * @param graph Input graph with reads
	 */
	public void correctErrors(AssemblyGraph graph) {
		long start = System.currentTimeMillis(); 
	
		AssemblyGraph copyGraph = graph.buildSubgraph(null);
		copyGraph.removeVerticesChimericReads();
		copyGraph.updateScores(0);
		filter.filterEdgesAndEmbedded(copyGraph, 0.5);
		pathsFinder.findPaths(copyGraph);
		int n =graph.getNumSequences();
		List<AssemblyPath> paths = copyGraph.getPaths();
		
		AssemblyPathReadsAligner aligner = new AssemblyPathReadsAligner();
		aligner.setLog(log);
		Map<Integer,AssemblyPath> selectedPathsMap = new HashMap<Integer, AssemblyPath>(paths.size());
		QualifiedSequenceList selectedPathsQS = new QualifiedSequenceList();
		Map<Integer,List<CalledGenomicVariant>> selectedPathsCalledIndels = new HashMap<Integer, List<CalledGenomicVariant>>(paths.size());
		Set<Integer> alignedReadIds = new HashSet<Integer>();
		int [] sequencePaths = new int [n];
		Arrays.fill(sequencePaths, 0);
		for(int i = 0; i < paths.size(); i++)
		{
			AssemblyPath path = paths.get(i);
			int pathId = i+1;
			path.setPathId(pathId);
			String sequenceName = ""+pathId;
			if(path.getPathLength()>10) {
				aligner.calculateConsensus(path);
				List<ReadAlignment> alignments = aligner.alignPathReads(copyGraph, path, numThreads);
				List<ReadAlignment> selectedAlns = new ArrayList<ReadAlignment>(alignments.size());
				for(ReadAlignment aln:alignments) {
					int readId = aln.getReadNumber();
					if(sequencePaths[readId]==0) {
						sequencePaths[readId] = pathId;
						aln.setSequenceName(sequenceName);
						alignedReadIds.add(aln.getReadNumber());
						selectedAlns.add(aln);
					}
				}
				selectedPathsMap.put(pathId, path);
				selectedPathsQS.add(new QualifiedSequence(sequenceName,path.getConsensus()));
				long usedMemory = (runtime.totalMemory()-runtime.freeMemory())/1000000;
				log.info("AssemblyPathReadsAligner. Correcting errors for reads aligned to path: "+path.getPathId()+" length: "+path.getPathLength()+" Memory (Mbp): "+usedMemory);
				//TODO: Define better ploidy
				List<CalledGenomicVariant> pathIndels = aligner.callIndels(path.getConsensus(), selectedAlns, 2);
				usedMemory = (runtime.totalMemory()-runtime.freeMemory())/1000000;
				log.info("AssemblyPathReadsAligner. Called indels in path: "+path.getPathId()+": "+pathIndels.size()+" Memory (Mbp): "+usedMemory);
				Collections.sort(pathIndels,GenomicRegionPositionComparator.getInstance());
				selectedPathsCalledIndels.put(pathId,pathIndels);
				correctErrors(graph, selectedAlns, path, pathIndels, sequencePaths);
				
			}
			
		}
		long endAln = System.currentTimeMillis();
		long timeAln = (endAln-start)/1000; 
		long usedMemory = runtime.totalMemory()-runtime.freeMemory();
		usedMemory/=1000000000;
		int unaligned = n - alignedReadIds.size();
		log.info("IndelErrorsCorrector. Aligned and corrected reads within paths: "+alignedReadIds.size()+" Unaligned: "+unaligned+". Time (s): "+timeAln+" Memory: "+usedMemory+" Correcting remaining reads.");
		
		correctRemainingReads(copyGraph, selectedPathsMap, selectedPathsQS, selectedPathsCalledIndels, alignedReadIds);
		long endCorr2 = System.currentTimeMillis();
		long timeCorr2 = (endCorr2-endAln)/1000;
		long timeTotal = (endCorr2-start)/1000;
		usedMemory = runtime.totalMemory()-runtime.freeMemory();
		usedMemory/=1000000000;
		log.info("IndelErrorsCorrector. Aligned and corrected remaining reads. Time process: "+timeCorr2+" Total time error correction (s): "+timeTotal+" Memory: "+usedMemory);	
	}

	

	private void correctErrors(AssemblyGraph graph, List<ReadAlignment> alignments, AssemblyPath path, List<CalledGenomicVariant> pathIndels, int [] sequencePaths) {
		long start = System.currentTimeMillis();
		String pathConsensus = path.getConsensus();
		
		ThreadPoolManager poolCorrection = new ThreadPoolManager(numThreads, 4*numThreads);
		int pathId = path.getPathId();
		int indexNextActive = 0;
		
		for(ReadAlignment aln:alignments) {
			int readId = aln.getReadNumber();
			if(sequencePaths[readId]!=pathId) {
				log.warning("Read "+readId+" "+aln.getReadName()+" corrected by path: "+sequencePaths[readId]+" current path: "+pathId);
				continue;
			}
			while (indexNextActive<pathIndels.size()) {
				GenomicRegion activeSegment = pathIndels.get(indexNextActive);
				if(activeSegment.getLast()<aln.getFirst()) {
					indexNextActive++;
				} else break;
			}
			int i = indexNextActive;
			try {
				poolCorrection.queueTask(()->correctRead(graph.getSequence(readId), aln, pathConsensus, pathIndels, i, false));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			poolCorrection.terminatePool();
		} catch (InterruptedException e) {
			// TODO Better handling
			e.printStackTrace();
		}
		long time = (System.currentTimeMillis()-start)/1000;
		long usedMemory = runtime.totalMemory()-runtime.freeMemory();
		usedMemory/=1000000000;
		log.info("IndelErrorsCorrector. Corrected reads aligned to path: "+path.getPathId()+" in "+time+" seconds. Memory: "+usedMemory);
	}
	
	private void correctRead (QualifiedSequence read, ReadAlignment aln, String pathConsensus, List<CalledGenomicVariant> indels, int firstIndelPos, boolean chimeric) {
		int debugIdx = -1;
		int readId = aln.getReadNumber();
		int nI = indels.size();
		List<Integer> referencePositionsCalledIndels = new ArrayList<Integer>();
		for(int j=firstIndelPos;j<nI;j++) {
			CalledGenomicVariant calledIndel = indels.get(j);
			referencePositionsCalledIndels.add(calledIndel.getFirst());
			referencePositionsCalledIndels.add(calledIndel.getLast());
			if (calledIndel.getLast()>aln.getLast()) break;
		}
		Map<Integer,Integer> refToReadMap = aln.getAlignedReadPositions(referencePositionsCalledIndels);
		//System.out.println("Read "+readId+" step 1");
		String alignedRead = aln.getReadCharacters().toString();
		StringBuilder correctedRead = new StringBuilder(alignedRead.length());
		Map<Integer,GenomicVariant> calls = aln.getIndelCallsByAlignedReadPos();
		if(readId == debugIdx) System.out.println("AlignmentBasedErrorCorrection. Correcting read: "+readId+" indel calls: "+calls.size()+" aln: "+aln);
		int nextPos = 0;
		int lastRef = 0;
		//Case for alignment without indels
		if(calls == null) calls = new HashMap<Integer, GenomicVariant>();
		for(Map.Entry<Integer, GenomicVariant> entry:calls.entrySet()) {
			int posRead = entry.getKey();
			GenomicVariant readIndel = entry.getValue();
			if(readIndel.length()>3) continue;
			if(posRead>nextPos) {
				//Apply homozygous indels not called in this alignment
				for(;firstIndelPos<nI;firstIndelPos++) {
					CalledGenomicVariant calledIndel = indels.get(firstIndelPos);
					if(readId == debugIdx) System.out.println("AlignmentBasedErrorCorrection. ReadId: "+readId+" Next active indel: "+calledIndel.getFirst()+" "+calledIndel.getLast()+" ref limits: "+lastRef+" "+readIndel.getFirst()+" read limits: "+nextPos+" "+posRead);
					if(calledIndel.getFirst() > lastRef && calledIndel.getLast()<readIndel.getFirst()) {
						if(calledIndel.isHeterozygous()) continue;
						if(calledIndel.getAlleles().length<2) System.err.println("Filtered indel with only one allele: "+calledIndel.getFirst()+" "+calledIndel.getLast()+" genotype: "+calledIndel.getCalledAlleles()[0]+" allele: "+calledIndel.getAlleles()[0]+" homoref: "+calledIndel.isHomozygousReference()+" undecided: "+calledIndel.isUndecided());
						int diffLength = calledIndel.getAlleles()[0].length()-calledIndel.getAlleles()[1].length();
						if(Math.abs(diffLength)>3) continue;
						Integer readPosStartVar = refToReadMap.get(calledIndel.getFirst());
						Integer readPosEndVar = refToReadMap.get(calledIndel.getLast()); 
						if(readPosStartVar!=null && readPosEndVar!=null && readPosStartVar > nextPos && readPosEndVar<posRead && readPosStartVar<readPosEndVar) {
							String currentSegment = alignedRead.substring(readPosStartVar,readPosEndVar+1);
							String correctedAllele = calledIndel.getCalledAlleles()[0];
							if(Math.abs(currentSegment.length()-correctedAllele.length())>3) continue;
							//if(readId==debugIdx || currentSegment.length()>30) System.out.println("AlignmentBasedErrorCorrection. Adding called indel for read: "+readId+" spanning "+readPosStartVar+" "+readPosEndVar+" segment: "+currentSegment+" alleles var: "+calledVar.getAlleles()[0]+" "+calledVar.getAlleles()[1]+" called allele: "+correctedAllele);
							correctedRead.append(alignedRead.substring(nextPos, readPosStartVar));
							correctedRead.append(correctedAllele);
							nextPos = readPosEndVar+1;
						}
					} else if (calledIndel.getLast()>=readIndel.getFirst()) {
						break;
					}
				}
				correctedRead.append(alignedRead.substring(nextPos, posRead+1));
			}
			nextPos = posRead+1;
			//Correct if indel not called in this region
			boolean correctIndel = true;
			for(int j=firstIndelPos;j<nI;j++) {
				GenomicRegion activeSegment = indels.get(j);
				if(activeSegment.getLast()>=readIndel.getFirst()) {
					correctIndel = (activeSegment.getFirst()>readIndel.getLast());
					break;
				}
			}
			if(correctIndel) {
				if(readId == debugIdx) System.out.println("AlignmentBasedErrorCorrection. Read id: "+readId+" Correcting indel at: "+readIndel.getFirst()+" "+readIndel.getLast()+" length "+readIndel.length()+" outside active regions");
				if(readIndel.getLast()==readIndel.getFirst()+1) {
					//Insertion
					nextPos +=readIndel.length();
				} else {
					//Deletion
					correctedRead.append(pathConsensus.substring(readIndel.getFirst(), readIndel.getLast()-1));
				}
			}
			lastRef = readIndel.getLast();
		}
		if(nextPos<aln.getReadLength()) {
			//Apply homozygous indels not called in this alignment
			for(int j=firstIndelPos;j<nI;j++) {
				CalledGenomicVariant calledIndel = indels.get(j);
				if(readId == debugIdx) System.out.println("AlignmentBasedErrorCorrection. ReadId: "+readId+" Next called indel: "+calledIndel.getFirst()+" "+calledIndel.getLast()+" alleles: "+calledIndel.getAlleles()[0]+" "+calledIndel.getAlleles()[1]+" heterozygous: "+calledIndel.isHeterozygous()+" ref limits: "+lastRef+" "+aln.getLast()+" read limits: "+nextPos+" "+aln.getReadLength());
				if(calledIndel.getFirst() > lastRef && calledIndel.getLast()<aln.getLast()) {
					if(calledIndel.isHeterozygous()) continue;
					int diffLength = calledIndel.getAlleles()[0].length()-calledIndel.getAlleles()[1].length();
					if(Math.abs(diffLength)>3) continue;
					Integer readPosStartVar = refToReadMap.get(calledIndel.getFirst());
					Integer readPosEndVar = refToReadMap.get(calledIndel.getLast()); 
					if(readPosStartVar!=null && readPosEndVar!=null && readPosStartVar> nextPos && readPosEndVar<aln.getReadLength() && readPosStartVar<readPosEndVar) {
						String currentSegment = alignedRead.substring(readPosStartVar,readPosEndVar+1);
						String correctedAllele = calledIndel.getCalledAlleles()[0];
						if(Math.abs(currentSegment.length()-correctedAllele.length())>3) continue;
						//if(readId==debugIdx || currentSegment.length()>30) System.out.println("AlignmentBasedErrorCorrection. Adding called indel for read: "+readId+" spanning "+readPosStartVar+" "+readPosEndVar+" segment: "+currentSegment+" alleles var: "+calledVar.getAlleles()[0]+" "+calledVar.getAlleles()[1]+" called allele: "+correctedAllele);
						correctedRead.append(alignedRead.substring(nextPos, readPosStartVar));
						correctedRead.append(correctedAllele);
						nextPos = readPosEndVar+1;
					} else {
						if(readId==debugIdx) System.out.println("AlignmentBasedErrorCorrection. Discarded called indel for read: "+readId+" spanning "+readPosStartVar+" "+readPosEndVar+" read limits: "+nextPos+" "+aln.getReadLength());
					}
				} else if (calledIndel.getLast()>=aln.getLast()) {
					break;
				}
			}
			correctedRead.append(alignedRead.substring(nextPos));
		}
		//Removing soft clips within the sequence if chimeric
		if(chimeric) {
			if(aln.getSoftClipEnd()>100) {
				if(readId == debugIdx) System.out.println("Removing "+aln.getSoftClipEnd()+" bp from end of read "+readId+" "+aln);
				correctedRead.delete(correctedRead.length()-aln.getSoftClipEnd(),correctedRead.length());
			}
			if(aln.getSoftClipStart()>100) {
				if(readId == debugIdx) System.out.println("Removing "+aln.getSoftClipStart()+" bp from start of read "+readId+" "+aln);
				correctedRead.delete(0, aln.getSoftClipStart());
			}
		}
		 
		/**/
		
		//if(readId == debugIdx) System.out.println("AlignmentBasedErrorCorrection. Correcting read: "+readId+" initial read: "+alignedRead+" corrected: "+correctedRead);
		aln.setReadCharacters(null);
		CharSequence correctedReadS;
		if(aln.isNegativeStrand()) {
			correctedReadS = DNASequence.getReverseComplement(correctedRead);
		} else {
			correctedReadS = new DNASequence(correctedRead);
		}
		read.setCharacters(correctedReadS);
	}
	
	private void correctRemainingReads(AssemblyGraph graph, Map<Integer,AssemblyPath> selectedPathsMap, QualifiedSequenceList pathSequences, Map<Integer,List<CalledGenomicVariant>> selectedPathsCalledIndels, Set<Integer> alignedReadIds) {
		
		int n = graph.getNumSequences();
		ReadsAligner aligner = new ReadsAligner();
		ReferenceGenome genome = new ReferenceGenome(pathSequences);
		aligner.setGenome(genome);
		aligner.setNumThreads(numThreads);
		
		
		long usedMemory = runtime.totalMemory()-runtime.freeMemory();
		usedMemory/=1000000000;
		log.info("IndelErrorsCorrector. Built minimizers for consensus sequences of: "+genome.getNumSequences()+" paths. Memory: "+usedMemory);
		ThreadPoolManager poolAlign = new ThreadPoolManager(numThreads, 1000);
		for(int i=0;i<n;i++) {
			if(alignedReadIds.contains(i)) continue;
			QualifiedSequence seq = graph.getSequence(i);
			boolean chimeric = graph.isChimeric(i);
			if(seq==null) continue;
			final int id = i;
			try {
				poolAlign.queueTask(()->correctRemainingReadProcess(selectedPathsMap, aligner, id, seq, selectedPathsCalledIndels,chimeric));
			} catch (InterruptedException e) {
				// TODO: Better handling
				e.printStackTrace();
			}
		}
		try {
			poolAlign.terminatePool();
		} catch (InterruptedException e) {
			// TODO Better handling
			e.printStackTrace();
		}
	}

	private void correctRemainingReadProcess(Map<Integer,AssemblyPath> selectedPathsMap, ReadsAligner aligner, int id, QualifiedSequence seq, Map<Integer,List<CalledGenomicVariant>> selectedPathsCalledIndels, boolean chimeric) {
		List<ReadAlignment> alns = aligner.alignRead(new RawRead(seq.getName(), seq.getCharacters(), null));
		if(alns.size()==0) {
			System.out.println("Unaligned read "+seq.getName()+" to consensus");
			return;
		}
		ReadAlignment aln = alns.get(0);
		aln.setReadNumber(id);
		int pathId = Integer.parseInt(aln.getSequenceName());
		List<CalledGenomicVariant> calledIndelsPath = selectedPathsCalledIndels.get(pathId);
		String pathConsensus = selectedPathsMap.get(pathId).getConsensus();
		correctRead(seq, aln, pathConsensus, calledIndelsPath, 0, chimeric);
	}
}
