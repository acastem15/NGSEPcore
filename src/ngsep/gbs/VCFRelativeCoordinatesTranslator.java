package ngsep.gbs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import ngsep.alignments.ReadAlignment;
import ngsep.alignments.ReadAlignmentPair;
import ngsep.alignments.ReadsAligner;
import ngsep.alignments.io.ReadAlignmentFileReader;
import ngsep.genome.GenomicRegionComparator;
import ngsep.genome.ReferenceGenome;
import ngsep.genome.ReferenceGenomeFMIndex;
import ngsep.sequences.DNAMaskedSequence;
import ngsep.sequences.DNASequence;
import ngsep.sequences.QualifiedSequence;
import ngsep.sequences.RawRead;
import ngsep.sequences.io.FastaFileReader;
import ngsep.variants.CalledGenomicVariant;
import ngsep.variants.CalledSNV;
import ngsep.variants.GenomicVariant;
import ngsep.variants.GenomicVariantAnnotation;
import ngsep.variants.GenomicVariantImpl;
import ngsep.variants.SNV;
import ngsep.variants.Sample;
import ngsep.vcf.VCFFileHeader;
import ngsep.vcf.VCFFileReader;
import ngsep.vcf.VCFFileWriter;
import ngsep.vcf.VCFRecord;

public class VCFRelativeCoordinatesTranslator {

	private Logger log = Logger.getLogger(VCFRelativeCoordinatesTranslator.class.getName());
	private String outFile="output.vcf";
	private String filenameConsensusFA;
	private String filenameAlignmentBAM;
	private String filenameRelativeVCF;
	private ReferenceGenome refGenome;
	private ReferenceGenomeFMIndex refIndex = null;
	private Map<String, ReadAlignment> alignmentsHash;
	int numTranslatedRecords = 0;
	int totalRecords = 0;
	int skippedRecords = 0;
	int nullReadName = 0;
	int TruePosNotAlign = 0;
	
	int consensusTot = 0;
	int pairedConsensus = 0;
	int singleConsensus = 0;
	int biallelic = 0;
	int triallelic = 0;
	
	
	// issues with translation
	int untranslated = 0;
	int notSNV = 0;
	int nonVariant = 0;
	int refNotInAlleles = 0;
	int notRefSeq = 0;
	int notDNA = 0;
	int translatedmapped = 0;
	int refSeqLess0 = 0;
	int trueCallsNull = 0;
	int recordWihoutAlign = 0;
	
	// issues with mapping
	int unmappedReadSingle = 0;
	int unmappedReadPaired = 0;
	int unmappedRead = 0;
	int singlemapfor = 0;
	int singlemaprev = 0;
	int unpaired = 0;
	int partial = 0;
	int noAlignSingle = 0;
	int noAlignPaired = 0;
	int oddAlign = 0;
	int unmappedCluster = 0;

	
//	public static void main(String[] args) throws Exception {
//		VCFRelativeCoordinatesTranslator instance = new VCFRelativeCoordinatesTranslator();
//		int i = CommandsDescriptor.getInstance().loadOptions(instance, args);
//		instance.filenameAlignmentBAM = args[i++];
//		instance.filenameRelativeVCF = args[i++];
//		String refGenomeFile = args[i++];
//		instance.outFile = args[i++];
//		instance.run(refGenomeFile);
//	}
	public static void main(String[] args) throws Exception {
		VCFRelativeCoordinatesTranslator instance = new VCFRelativeCoordinatesTranslator();
		if("fa".equals(args[0])) {
			instance.filenameConsensusFA = args[1];
		} else {
			instance.filenameAlignmentBAM = args[1];
		}
		
		instance.filenameRelativeVCF = args[2];
		instance.refGenome= new ReferenceGenome(args[3]);
		instance.refIndex = ReferenceGenomeFMIndex.load(instance.refGenome, args[4]);
		instance.outFile = args[5];
		instance.run();
	}

	public void run() throws Exception {
		if (filenameConsensusFA!=null) alignConsensusSequences();
		if (filenameAlignmentBAM!=null) getAlignmentsFromBAM();
		System.out.println("Loaded a total of " + alignmentsHash.size() + " alignments.");
		
		translate();
//		explore();
	}

	/**
	 *  For each record on the RelativeVCF, this method translates and rewrites. 
	 * @throws Exception
	 */
	private void translate() throws Exception {
		List<VCFRecord> translatedRecords = new ArrayList<>();
		List<Sample> samples;
		VCFFileHeader header = VCFFileHeader.makeDefaultEmptyHeader();
		
		try (VCFFileReader vcfOpenFile = new VCFFileReader(filenameRelativeVCF)) {
			samples = vcfOpenFile.getHeader().getSamples();
			header.addSamples(samples);
			Iterator<VCFRecord> vcfReader = vcfOpenFile.iterator();
			// Iterate over vcfRecords
			while(vcfReader.hasNext()) {
				VCFRecord translatedRecord = null;
				VCFRecord record = vcfReader.next();
				
				
					
				
				String clusterID = record.getSequenceName();
				ReadAlignment alignment =  alignmentsHash.get(clusterID);
				if(alignment != null ) {
					// If variant is SNP
					if(record.getVariant().isSNV()) {
						translatedRecord = translateRecord(alignment, record, header);
					} else {
						notSNV++;
						untranslated++;
					}
					if(translatedRecord != null) {
						translatedRecords.add(translatedRecord);
						numTranslatedRecords++;
					} else {
						untranslated++;
					}
				} else recordWihoutAlign++;
				totalRecords++;
			}
			
		}
		Collections.sort(translatedRecords,new GenomicRegionComparator(refGenome.getSequencesMetadata()));
		VCFFileWriter writer = new VCFFileWriter ();
		try (PrintStream mappedVCF = new PrintStream(outFile);
				PrintStream info = new PrintStream(outFile + ".info")) {
			writer.printHeader(header, mappedVCF);
			writer.printVCFRecords(translatedRecords, mappedVCF);
			info.println("10 softclip");
			info.println("Total number of records in relative VCF: " + totalRecords);
			info.println("Number of translated records: " + numTranslatedRecords);
			info.println("Number of translater biallelic variants: " + biallelic);
			info.println("Total number of consensus sequences: " + consensusTot);
			info.println("Single Consensus: " + singleConsensus);
			info.println("Paired Consensus: " + pairedConsensus);
			info.println("------ Issues with translation ------");
			info.println("Number of records without an alignment: "+recordWihoutAlign);
			info.println("Number of records with an alignment: " + translatedmapped);
			info.println("Number of records not translated even though they had an alignment: " + untranslated);
			info.println("Number of records that are triallelic variants: " + triallelic);
			info.println("Number of records whose Cluster ID was unmapped: " + unmappedCluster);
			info.println("Number of records where matching reference sequence is not DNA: " + notDNA); 
			info.println("Number of records that are not SNV: " + notSNV);
			info.println("Number of records where no reference sequence was found: " + notRefSeq);
			info.println("Number of records where reference sequence doesn't exist (-1): " + refSeqLess0);
			info.println("Number of records where no calls found (matches number of triallelic): " + trueCallsNull);
			info.println("------ Issues with mapping ------");
			info.println("Number of unmapped consensus: " + unmappedRead);
			info.println("Number of unmapped single consensus: " + unmappedReadSingle);
			info.println("Number of unmapped paired consensus: " + unmappedReadPaired);
			info.println("------ Issues with paired mapping ------");
			info.println("Unmapped paired consensus: " + unmappedReadPaired);
			info.println("Number of consensus where only reverse consensus aligned: " + singlemapfor);
			info.println("Number of consensus where only forward consensus aligned: " + singlemaprev);
			info.println("Number of consensus where neither forward nor reverse aligned: " + noAlignPaired);
			info.println("Number of unpaired consensus: " + unpaired);
			info.println("Number of partial consensus: " + partial);
			info.println("Odd alignment: "+oddAlign);
		}
	}
	
	/**
	 * This method takes in an alignment to the reference and a record 
	 * and translates each record to match the reference.
	 * @param algn
	 * @param record
	 * @return translated record
	 */
	private VCFRecord translateRecord(ReadAlignment algn, VCFRecord record, VCFFileHeader header) {
		translatedmapped++;
		VCFRecord translatedRecord = null;
		List<CalledGenomicVariant> trueCalls = new ArrayList<>();

		// True position of variant with respect to reference forward strand (1-based)
		int truePos;
		
		// Reference nucleotide as interpreted by the alignment to the reference
		char trueRef;
		
		// Position of variant with respect to the de-novo alignment 1-based
		// Relative reference base (the base at position of variant in the consensus seq)
		int zeroBasedRelativePos = record.getFirst()-1;
		
		// Variant as found in de-novo vcf
		GenomicVariant relativeVar = record.getVariant();
		
		// Alternative Allele based on de-novo VCF
		String[] relativeAlleles = relativeVar.getAlleles();
		
		int[] filedsFormat = record.getFieldsFormat();
		
		// IF the reference is not in the relativeAlleles, the variant is likely triallelic
		boolean refInRelativeAllels = false;
		
		// Will be something like Cluster-####
		String seqName = algn.getSequenceName();
		
		if(seqName==null) {
			unmappedCluster++;
			unmappedRead++;
			return null;
		}
		
		
		//-1 if the read position does not align with the reference 
		truePos = algn.getReferencePosition(zeroBasedRelativePos);
//		if(algn.isNegativeStrand()) {
//		
//			truePos = algn.getLast() - zeroBasedRelativePos;
//			
//		} else {
//				
//			//-1 if the read position does not align with the reference 
//			//truePos = algn.getReferencePosition(zeroBasedRelativePos);
//			truePos = algn.getFirst() + zeroBasedRelativePos;
//		}
			
		
		if(truePos<=0) {
			refSeqLess0++;
			return null;
		}
		
		CharSequence trueRefSeq = refGenome.getReference(seqName, truePos, truePos);
		if(trueRefSeq == null) {
			notRefSeq++;
			return null;
		}
		
		if(!DNASequence.isDNA(trueRefSeq)) {
			notDNA++;
			return null;
		} else {
			trueRef = trueRefSeq.charAt(0);
		}
		//TODO: Translate indels
		List<String> refBasedAlleles = new ArrayList<>();
		refBasedAlleles.add(""+trueRef);
		for(String allele:relativeAlleles) {
			if(!DNASequence.isDNA(allele)) continue;
			if(algn.isNegativeStrand()) {
				allele = DNASequence.getReverseComplement(allele).toString();
			}
			if(allele.charAt(0)==trueRef) refInRelativeAllels = true;
			if(!refBasedAlleles.contains(allele)) {
				refBasedAlleles.add(allele);
			}
		}
		GenomicVariant variant;
		if(refBasedAlleles.size()== 2) {
			variant = new SNV(seqName, truePos, trueRef, refBasedAlleles.get(1).charAt(0));
			biallelic++;
		} else if (refBasedAlleles.size()>= 3) {
			variant = new GenomicVariantImpl(seqName, truePos, refBasedAlleles);
			triallelic++;
		} else {
			nonVariant++;
			return null;
		}
		variant.setVariantQS(relativeVar.getVariantQS());
		if(!refInRelativeAllels) {
			refNotInAlleles++;
			
		}
		// get the calls for this record.
		List<CalledGenomicVariant> calls = record.getCalls();		
			
		// Translate genotypes 
		//-1 for undecided, 0 for homozygous reference, 1 for heterozygous, 2 for homozygous variant
		for(CalledGenomicVariant relativeCall: calls) {
			String [] calledAlleles = relativeCall.getCalledAlleles();
			int [] acgtCounts = relativeCall.getAllCounts();
			if (algn.isNegativeStrand()) {
				for(int i=0;i<calledAlleles.length;i++) {
					calledAlleles[i] = DNASequence.getReverseComplement(calledAlleles[i]).toString();
				}
				if (acgtCounts!=null) {
					int tmp = acgtCounts[0];
					acgtCounts [0] = acgtCounts[3];
					acgtCounts[3] = tmp;
					tmp = acgtCounts[1];
					acgtCounts [1] = acgtCounts[2];
					acgtCounts[2] = tmp;
				}
			}
			//TODO: Translate well acn for polyploids
			short [] acn = new short[refBasedAlleles.size()];
			Arrays.fill(acn, (short)0);
			if(variant instanceof SNV) {
				byte genotype = CalledGenomicVariant.GENOTYPE_UNDECIDED;
				if(calledAlleles.length==2) {
					genotype = CalledGenomicVariant.GENOTYPE_HETERO;
					acn[0] = acn[1] = 1;
				}
				else if (calledAlleles.length==1) {
					if(calledAlleles[0].charAt(0)!=trueRef) {
						genotype = CalledGenomicVariant.GENOTYPE_HOMOALT;
						acn[1]=2;
					} else {
						genotype = CalledGenomicVariant.GENOTYPE_HOMOREF;
						acn[0]=2;
					}
				}
				CalledSNV trueCall = new CalledSNV((SNV)variant, genotype);
				trueCall.setGenotypeQuality(relativeCall.getGenotypeQuality());
				trueCall.setTotalReadDepth(relativeCall.getTotalReadDepth());
				trueCall.setAllelesCopyNumber(acn);
				if(acgtCounts!=null)  trueCall.setAllBaseCounts(acgtCounts);
				trueCalls.add(trueCall);
			}
		}
		if(trueCalls.size()>0) {
			
			translatedRecord = new VCFRecord(variant, filedsFormat, trueCalls, header);
			translatedRecord.addAnnotation(new GenomicVariantAnnotation(variant, "DENOVOCLUSTER", relativeVar.getSequenceName()));
			translatedRecord.addAnnotation(new GenomicVariantAnnotation(variant, "DENOVOCLUSTERPOS", relativeVar.getFirst()));
			translatedRecord.addAnnotation(new GenomicVariantAnnotation(variant, "DENOVOCLUSTERCONSENSUS", relativeVar.getReference()));
		} else trueCallsNull++;
		return translatedRecord;
	}
	
	private void alignConsensusSequences() throws IOException {
		System.out.println("Aligning consensus sequences");
		this.alignmentsHash = new HashMap<String, ReadAlignment>();
		
		ReadsAligner aligner = new ReadsAligner();
		aligner.setGenome(refGenome);
		if(refIndex!=null) aligner.setFmIndex(refIndex);
		else aligner.setFmIndex(new ReferenceGenomeFMIndex(refGenome));
		
		try (FastaFileReader reader = new FastaFileReader(filenameConsensusFA);
				PrintStream debug = new PrintStream(outFile + ".debug")) {
			reader.setLog(log);
			Iterator<QualifiedSequence> it = reader.iterator();
			for(int i=0;it.hasNext();i++) {
				consensusTot++;
				QualifiedSequence consensus = it.next();
				String algnName = consensus.getName();
				debug.println(algnName);
				if(algnName.startsWith("Cluster_")) {
					algnName = algnName.substring(8);
				}
				String seq = consensus.getCharacters().toString();
				if(seq.length() == 0) continue;
				int indexN = seq.indexOf('N');
				if(indexN <=30 || indexN>=seq.length()-30) {
					singleConsensus++;
					RawRead read = new RawRead(algnName, consensus.getCharacters(),RawRead.generateFixedQSString('5', consensus.getLength()));
					List<ReadAlignment> alns = aligner.alignRead(read, true);
					System.out.println("Read: "+algnName+" Alns single "+alns.size());
					if((i+1)%10000==0) System.out.println("Aligning consensus sequence "+(i+1)+" id: "+consensus.getName()+" sequence: "+seq+" alignments: "+alns.size()+". Total unmapped "+unmappedRead);
					if(alns.size()==0) {
						unmappedReadSingle++;
						unmappedRead++;
						continue;
					}
					ReadAlignment first = alns.get(0);
					alignmentsHash.put(algnName,first);
				} else {
					DNAMaskedSequence seq1 = new DNAMaskedSequence(seq.substring(0,indexN));
					DNAMaskedSequence seq2 = new DNAMaskedSequence(seq.substring(indexN+1));
					
					RawRead read1 = new RawRead(algnName, seq1, RawRead.generateFixedQSString('5', seq1.length()));
					RawRead read2 = new RawRead(algnName, seq2.getReverseComplement(), RawRead.generateFixedQSString('5', seq2.length()));
					List<ReadAlignment> alns1 = aligner.alignRead(read1, true);
					List<ReadAlignment> alns2 = aligner.alignRead(read2, true);
					System.out.println("Read 1: "+algnName+" Alns 1: "+alns1+" alns 2: "+alns2);
					pairedConsensus++;
					if(alns1.size()==0|| alns2.size()==0) {
						if(alns1.size()==0 && alns2.size()!=0) singlemapfor++;
						else if(alns1.size()!=0 && alns2.size()==0) singlemaprev++;
						else noAlignPaired++;
						unmappedReadPaired++;
						unmappedRead++;
						continue;
					}
					List<ReadAlignmentPair> pairs = aligner.findPairs(alns1, alns2, true);
					if(pairs.size()==0) {
						unpaired++;
						unmappedReadPaired++;
						unmappedRead++;
						continue;
					}
					ReadAlignmentPair first = pairs.get(0);
					ReadAlignment aln1 = first.getAln1();
					ReadAlignment aln2 = first.getAln2();
					debug.println("First algn aln1: " + aln1.getReadCharacters() + "\t" + aln1.getFlags() + "\t" + aln1.getFirst() + "\t" + aln1.getCigarString());
					debug.println("Second algn aln2: " + aln2.getReadCharacters() + "\t" + aln2.getFlags() + "\t" + aln2.getFirst() + "\t" + aln2.getCigarString());
					if(aln1.isPartialAlignment(10) || aln2.isPartialAlignment(10)) {
						partial++;
						unmappedReadPaired++;
						unmappedRead++;
						continue;
					}
					int posN1 = aln1.getLast()+1; 
					int posN2 = aln2.getLast()+1;
					if(aln1.getFirst()<aln2.getLast()) {
				 		
						String cigar = aln1.getCigarString();
						int last;
						if (posN1<aln2.getFirst()) {
							last = aln2.getLast();
							//The N character
							cigar+="1M";
							if (posN1+1<aln2.getFirst()) cigar+=(aln2.getFirst()-posN1-1)+"N";
							cigar+=aln2.getCigarString();
						} else {
							//Odd alignment. skip second part
							last=aln1.getLast();
							cigar+=""+(seq2.length()+1)+"S";
						}
						System.out.println("Combined CIGAR: "+cigar);
						ReadAlignment combined = new ReadAlignment(aln1.getSequenceIndex(), aln1.getFirst(), last, seq.length(), 0);
						combined.setSequenceName(aln1.getSequenceName());
						combined.setCigarString(cigar);
						combined.setReadCharacters(consensus.getCharacters());
						combined.setAlignmentQuality(aln1.getAlignmentQuality());
						debug.println("Combined alignment (aln1 < aln2): " + combined.getReadCharacters() + "\t" +  combined.getFirst() + "\t" + combined.getCigarString());
						if(pairs.size()>1) combined.setAlignmentQuality((byte)10);
						alignmentsHash.put(algnName,combined);
					} else if (aln2.getFirst() < aln1.getLast()) {
						
						String cigarNonOverlap = aln2.getCigarString();
						//The N character
						cigarNonOverlap+="1M";
						if (posN2+1<aln1.getFirst()) cigarNonOverlap+=(aln1.getFirst()-posN2-1)+"N";
						int firstPosCombined;
						String cigar;
						if(posN2<aln1.getFirst()) {
							firstPosCombined = aln2.getFirst();
							cigar = cigarNonOverlap;
						} else {
							firstPosCombined = aln1.getFirst();
							cigar = ""+(seq2.length()+1)+"S";
						}
						cigar+=aln1.getCigarString();
						System.out.println("Combined CIGAR: "+cigar);
						ReadAlignment combined = new ReadAlignment(aln1.getSequenceIndex(), firstPosCombined, aln1.getLast(), seq.length(), 16);
						combined.setSequenceName(aln1.getSequenceName());
						combined.setCigarString(cigar);
						combined.setReadCharacters(DNAMaskedSequence.getReverseComplement(consensus.getCharacters()));
						combined.setAlignmentQuality(aln1.getAlignmentQuality());
						debug.println("Combined alignment aln2 < aln1): " + combined.getReadCharacters() + "\t" +  combined.getFirst() + "\t" + combined.getCigarString());
						if(pairs.size()>1) combined.setAlignmentQuality((byte)10);
						alignmentsHash.put(algnName,combined);
					} else {
						oddAlign++;
						unmappedReadPaired++;
						unmappedRead++;
					}
					debug.println();
					debug.println();
				}
			}
		}
		
	}
	
	// Must be change replace method to an index-based approach to avoid replacing Skips inside the cigar.
	private ReadAlignment removeStartSoftClip(ReadAlignment algn, int maxAllowedSkips) {
		int operator = algn.getCigarItemOperator(0);
		String cigar = algn.getCigarString();
		ReadAlignment algn_1 = algn;
		// if starts with skips
		if(operator == 6) {
			int length = algn.getCigarItemLength(0);
			int newLength = length;
			if(length < maxAllowedSkips) {
				//if second operator is M
				System.out.println("Length: " + length);
				if(algn.getCigarItemOperator(1) == 3) {
					newLength = length + algn.getCigarItemLength(1);
					String partialOldCigar = length + "S" + algn.getCigarItemLength(1) + "M";
					String partialNewCigar = newLength + "M";
					cigar = cigar.replace(partialOldCigar, partialNewCigar);
				} else {
					String partialOldCigar = length + "S"; 
					String partialNewCigar = length + "M"; 
					cigar = cigar.replace(partialOldCigar, partialNewCigar);
				}
				algn_1 = new ReadAlignment(algn.getSequenceName(), algn.getFirst() - length, algn.getLast(), algn.length()+length, algn.getFlags());
				algn_1.setCigarString(cigar);
			}
			
		}
		
		return algn_1;
	}
	
	// Must be change replace method to an index-based approach to avoid replacing Skips inside the cigar.
	private ReadAlignment removeEndSoftClip(ReadAlignment algn, int maxAllowedSkips) {
		int operator = algn.getCigarItemOperator(algn.getNumCigarItems()-1);
		String cigar = algn.getCigarString();
		ReadAlignment algn_1 = algn;
		// if starts with skips
		if(operator == 6) {
			int length = algn.getCigarItemLength(algn.getNumCigarItems()-1);
			int newLength = length;
			if(length < maxAllowedSkips) {
				//if second operator is M
				System.out.println("Length: " + length);
				if(algn.getCigarItemOperator(algn.getNumCigarItems()-2) == 3) {
					newLength = length + algn.getCigarItemLength(algn.getNumCigarItems()-2);
					String partialOldCigar = algn.getCigarItemLength(algn.getNumCigarItems()-2) + "M" + length + "S";
					String partialNewCigar = newLength + "M";
					cigar = cigar.replace(partialOldCigar, partialNewCigar);
				} else {
					String partialOldCigar = length + "S"; 
					String partialNewCigar = length + "M"; 
					cigar = cigar.replace(partialOldCigar, partialNewCigar);
				}
				algn_1 = new ReadAlignment(algn.getSequenceName(), algn.getFirst(), algn.getLast()+length, algn.length()+length, algn.getFlags());
				algn_1.setCigarString(cigar);
			}
			
		}
		
		return algn_1;
	}
	
	
	/**
	 * This method builds a hash with the provided alignments to be consulted at translation time
	 * @throws IOException
	 */
	private void getAlignmentsFromBAM() throws IOException {
		this.alignmentsHash = new HashMap<>();
		try (ReadAlignmentFileReader bamOpenFile = new ReadAlignmentFileReader(filenameAlignmentBAM);) {
			Iterator<ReadAlignment> bamReader = bamOpenFile.iterator();
			while(bamReader.hasNext()) {
				ReadAlignment algn = bamReader.next();
				String algnName = "";
				if(algn.getReadName().contains("_")) {
					algnName = algn.getReadName().split("_")[1];
				} else {
					algnName = algn.getReadName();
				}
				//int clusterId = Integer.parseInt(algnName);
				if(algn.isReadUnmapped()) {
					unmappedRead++;
				} else if(!algn.isSecondary()) {
					this.alignmentsHash.put(algnName, algn);
				}
			}
		}
		
	}
	
	
	public void explore() throws IOException {
		try (ReadAlignmentFileReader bamOpenFile = new ReadAlignmentFileReader(filenameAlignmentBAM);) {
			Iterator<ReadAlignment> bamReader = bamOpenFile.iterator();
			int maxIndex = 0;
			int minIndex = 100;
			while(bamReader.hasNext()) {
				ReadAlignment algn = bamReader.next();
//				System.out.println("Read Name: " + algn.getReadName());
				if(algn.getSequenceIndex() >= maxIndex) {
					maxIndex = algn.getSequenceIndex();
				}
				if(algn.getSequenceIndex() <= minIndex) {
					minIndex = algn.getSequenceIndex();
				}
//				System.out.println("Sequence index: " + algn.getSequenceIndex());
			}
			System.out.println("Max index: " + maxIndex);
			System.out.println("Min index: " + minIndex);
		}
	}
	
}
