package ngsep.transcriptome.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import ngsep.genome.GenomicRegion;
import ngsep.genome.GenomicRegionImpl;
import ngsep.genome.GenomicRegionPositionComparator;
import ngsep.genome.ReferenceGenome;
import ngsep.sequences.DNAMaskedSequence;
import ngsep.sequences.QualifiedSequence;
import ngsep.sequences.QualifiedSequenceList;
import ngsep.transcriptome.Gene;
import ngsep.transcriptome.ProteinTranslator;
import ngsep.transcriptome.Transcript;
import ngsep.transcriptome.TranscriptSegment;
import ngsep.transcriptome.Transcriptome;

public class GTF2TranscriptomeHandler {
	private ReferenceGenome genome;
	private QualifiedSequenceList sequenceNames;
	
	private Logger log = Logger.getLogger(GFF3TranscriptomeHandler.class.getName());
	
	
	public Logger getLog() {
		return log;
	}
	public void setLog(Logger log) {
		this.log = log;
	}
	
	public GTF2TranscriptomeHandler (ReferenceGenome genome) {
		this.genome = genome;
		sequenceNames = genome.getSequencesMetadata();
	}
	
	public QualifiedSequenceList getSequenceNames() {
		return sequenceNames;
	}
	/**
	 * Loads the genes, transcripts and exons information from the given map file
	 * @param filename Name of the file with the transcriptome description
	 * @throws IOException If the file can not be read
	 */
	public Transcriptome loadMap(String filename) throws IOException {
		Transcriptome answer = new Transcriptome(sequenceNames);
		Map<String,Gene> genes = new HashMap<>();
		try (FileReader reader = new FileReader(filename);
			 BufferedReader in = new BufferedReader(reader)) {
			Transcript transcript = null;
			List<GenomicRegion> rawExons = new ArrayList<>();
			String line = in.readLine();
			while (line != null) {
				if(line.charAt(0)=='#') {
					//Skip comments
					line = in.readLine();
					continue;
				}
				String [] items = line.split("\t|;");
				QualifiedSequence seq;
				try {
					seq = sequenceNames.addOrLookupName(items[0]);
				} catch (Exception e2) {
					log.warning("Can not load  feature at line: "+line+". Unrecognized sequence name. "+items[0]);
					line = in.readLine();
					continue;
				}
				int first;
				try {
					first = Integer.parseInt(items[3]);
				} catch (NumberFormatException e1) {
					log.warning("Error loading feature at line "+line+". Start coordinate must be a positive integer");
					line = in.readLine();
					continue;
				}
				int last;
				try {
					last = Integer.parseInt(items[4]);
				} catch (NumberFormatException e1) {
					log.warning("Error loading feature at line "+line+". End coordinate must be a positive integer");
					line = in.readLine();
					continue;
				}
				if(items[6].charAt(0)=='.') {
					//Ignoring feature without direction
					line = in.readLine();
					continue;
				}
				boolean negativeStrand = items[6].charAt(0)=='-';
				String type = items[2];
				Map<String,String> attributes = loadAttributes (items);
				String geneId = attributes.get("gene_id");
				if(geneId == null) {
					log.warning("Gene id not found at line: "+line);
					line = in.readLine();
					continue;
				}
				String transcriptId = attributes.get("transcript_id");
				if(transcriptId == null) {
					log.warning("Transcript id not found at line: "+line);
					line = in.readLine();
					continue;
				}
				String geneName = attributes.get("ref_gene_name");
				if(geneName==null) geneName = geneId;
				if("transcript".equals(type)) {
					processTranscript (transcript,rawExons);
					transcript = null;
					rawExons.clear();
					Gene gene = genes.get(geneId);
					if(gene == null) {
						//Write gene
						gene = new Gene(geneId, geneName,seq.getName(), first, last, negativeStrand);
						genes.put(geneId, gene);
					} else if (!seq.getName().equals(gene.getSequenceName())) {
						log.warning("Error loading transcript at line: "+line+" Inconsistent sequence name. Gene sequence name: "+gene.getSequenceName());
						line = in.readLine();
						continue;
					} else if (negativeStrand!=gene.isNegativeStrand()) {
						log.warning("Error loading transcript at line: "+line+" Inconsistent strand. Gene positive strand: "+gene.isPositiveStrand());
						line = in.readLine();
						continue;
					} else {
						if(first < gene.getFirst()) gene.setFirst(first);
						if(last> gene.getLast()) gene.setLast(last);
					}
					transcript = new Transcript(transcriptId, seq.getName(), first, last, negativeStrand);
					transcript.setGene(gene);
				} else if (transcript!=null && "exon".equals(type)) {
					if(!transcript.getId().equals(transcriptId)) {
						log.warning("Disorganized exon at line: "+line);
						line = in.readLine();
						continue;
					}
					if(transcript.getFirst()>first || transcript.getLast()<last) {
						System.err.println("Exon coordinates outside transcript coordinates. Line: "+line);
						line = in.readLine();
						continue;
					}
					rawExons.add(new GenomicRegionImpl(seq.getName(), first, last));
				}
				
				line = in.readLine();
			}
			processTranscript (transcript,rawExons);
		}
		
		
		return answer;
	}
	private Map<String, String> loadAttributes(String[] items) {
		Map<String, String> attributes = new HashMap<>();
		for(int i=8;i<items.length;i++) {
			String nextPair = items[i].trim();
			int j = nextPair.indexOf(' ');
			//System.out.println("Next pair: "+nextPair+". space chr: "+j);
			if(j>0 && j<nextPair.length()-1 && nextPair.charAt(j+1)=='"') {
				attributes.put(nextPair.substring(0,j), nextPair.substring(j+2,nextPair.length()-1));
			}
		}
		return attributes;
	}
	private void processTranscript(Transcript transcript, List<GenomicRegion> rawExons) {
		if(transcript==null || rawExons.size()==0) return; 
		StringBuilder mRNAB = new StringBuilder();
		Collections.sort(rawExons,GenomicRegionPositionComparator.getInstance());
		List<GenomicRegion> exonsByTranscript = new ArrayList<>(rawExons);
		for(GenomicRegion exon:rawExons) {
			CharSequence nextSeq = genome.getReference(exon);
			if(nextSeq==null) {
				System.err.println("Sequence for exon at "+exon.getSequenceName()+":"+exon.getFirst()+"-"+exon.getLast()+" could not be retrieved");
				return;
			}
			mRNAB.append(nextSeq);
		}
		String mRNA = mRNAB.toString();
		if(transcript.isNegativeStrand()) {
			mRNA = DNAMaskedSequence.getReverseComplement(mRNA);
			Collections.reverse(exonsByTranscript);
		}
		int maxL = -1;
		int startLongest = -1;
		ProteinTranslator translator = new ProteinTranslator();
		char [] mRNACharArray = mRNA.toCharArray();
		for(int i=0;i<mRNA.length();i++) {
			char [] nextP = translator.getProteinSequence(mRNACharArray,i,false);
			if(nextP.length>0 && nextP[0]=='M') {
				if(maxL < nextP.length) {
					maxL = nextP.length;
					startLongest = i;
				}
			}
		}
		if(startLongest<0) {
			log.warning("No protein found for "+transcript.getSequenceName()+":"+transcript.getFirst()+"-"+transcript.getLast()+" "+transcript.getId());
			return;
		}
		List<TranscriptSegment> segments = new ArrayList<>();
		char [] protein = translator.getProteinSequence(mRNACharArray,startLongest,false);
		int start3PUTR = startLongest+3*(protein.length+1);
		int exonRelativeFirst=0;
		int module = 0;
		for(GenomicRegion exon:exonsByTranscript) {
			int exonRelativeLast = exonRelativeFirst+exon.length()-1;
			if(exonRelativeLast<startLongest) {
				//Five prime UTR
				TranscriptSegment utr = new TranscriptSegment(transcript, exon.getFirst(), exon.getLast());
				utr.setStatus(TranscriptSegment.STATUS_5P_UTR);
				segments.add(utr);
			} else if (exonRelativeFirst>=start3PUTR) {
				//Three prime UTR
				TranscriptSegment utr = new TranscriptSegment(transcript, exon.getFirst(), exon.getLast());
				utr.setStatus(TranscriptSegment.STATUS_3P_UTR);
				segments.add(utr);
			} else if (startLongest<=exonRelativeFirst && exonRelativeLast<start3PUTR) {
				//CDS
				TranscriptSegment cds = new TranscriptSegment(transcript, exon.getFirst(), exon.getLast());
				cds.setStatus(TranscriptSegment.STATUS_CODING);
				cds.setFirstCodonPositionOffset(getPhase(module));
				segments.add(cds);
				int cdsLenPlusModule = module + exon.getLast() - exon.getFirst() + 1;
				module = cdsLenPlusModule % 3;
			} else {
				int cdsGenomicFirst = exon.getFirst();
				int cdsGenomicLast = exon.getLast();
				if (exonRelativeFirst<startLongest) {
					int diff = startLongest-exonRelativeFirst;
					TranscriptSegment utr;
					if(transcript.isPositiveStrand()) {
						cdsGenomicFirst +=diff;
						utr = new TranscriptSegment(transcript, exon.getFirst(), cdsGenomicFirst-1);
					} else {
						cdsGenomicLast -=diff;
						utr = new TranscriptSegment(transcript, cdsGenomicLast+1, exon.getLast());
						
					}
					utr.setStatus(TranscriptSegment.STATUS_5P_UTR);
					segments.add(utr);
				}
				int threePUTRFirst = 0;
				int threePUTRLast = 0;
				if (exonRelativeLast>=start3PUTR) {
					int diff = exonRelativeLast-start3PUTR+1;
					if(transcript.isPositiveStrand()) {
						cdsGenomicLast -=diff;
						threePUTRFirst = cdsGenomicLast+1;
						threePUTRLast = exon.getLast();
					} else {
						cdsGenomicFirst +=diff;
						threePUTRFirst = exon.getFirst();
						threePUTRLast = cdsGenomicFirst-1;
					}
				}
				TranscriptSegment cds = new TranscriptSegment(transcript, cdsGenomicFirst, cdsGenomicLast);
				cds.setStatus(TranscriptSegment.STATUS_CODING);
				cds.setFirstCodonPositionOffset(getPhase(module));
				segments.add(cds);
				int cdsLenPlusModule = module + cdsGenomicLast - cdsGenomicFirst + 1;
				module = cdsLenPlusModule % 3;
				if(threePUTRFirst>0) {
					TranscriptSegment utr = new TranscriptSegment(transcript, threePUTRFirst, threePUTRLast);
					utr.setStatus(TranscriptSegment.STATUS_3P_UTR);
					segments.add(utr);
				}
			}
			exonRelativeFirst=exonRelativeLast+1;
		}
	}
	private byte getPhase(int module) {
		byte phase = 0;
		if(module == 1) phase =2;
		if(module == 2) phase =1;
		return phase;
	}
}
