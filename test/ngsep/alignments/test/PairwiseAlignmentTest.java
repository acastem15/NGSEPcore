package ngsep.alignments.test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import ngsep.alignments.PairwiseAlignerDynamicKmers;
import ngsep.alignments.PairwiseAlignerSimpleGap;
import ngsep.alignments.PairwiseAlignerStaticBanded;
import ngsep.sequences.DNASequence;
import ngsep.sequences.LimitedSequence;
import ngsep.sequences.QualifiedSequence;

import ngsep.sequences.io.*;

public class PairwiseAlignmentTest extends TestCase {
	private CharSequence seq1 = ""; 
	private CharSequence seq2 = ""; 
	private int match =1; 

    private int mismatch = 1; 

    private int  indel = 2; 
	
	
	private int FindScore(CharSequence algseq1, CharSequence algseq2) {
		int cost = 0; 
		
		for (int i = 0; i<algseq1.length(); i++) {
			
			if (algseq1.charAt(i) == LimitedSequence.GAP_CHARACTER) {
				cost += insertionCost(algseq1.charAt(i),algseq2.charAt(i));
				
			}else if (algseq2.charAt(i) == LimitedSequence.GAP_CHARACTER) {
				cost += deletionCost(algseq1.charAt(i),algseq2.charAt(i));
			}else {
				cost+=matchMismatchCost(algseq1.charAt(i),algseq2.charAt(i)); 
			}
		}
		return cost; 
		
		
	}
	
    private int insertionCost(char l1, char l2){
        return -indel; 

    }
    private int deletionCost(char l1, char l2){
        return -indel; 

    }
    private int matchMismatchCost(char l1, char l2){
        if (l1 == l2){
            return match;

        }else{
            return -mismatch; 
        }

    }
	
	
	public void loadTwoSequences() {
		String file = "./dataTest/"+"exampleFLOGenes.fa"; 
		
		FastaSequencesHandler reader = new FastaSequencesHandler(); 
		reader.setSequenceType(DNASequence.class);
		reader.setKeepLowerCase(false);
		List<QualifiedSequence> sequences; 
		
		try {
		sequences = reader.loadSequences(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			sequences = null; 
		} 
		Random rand = new Random(); 
		int i = rand.nextInt(sequences.size()); 
		int j = rand.nextInt(sequences.size()); 

		
		
		seq1 = sequences.get(i).getCharacters(); 
		seq2 = sequences.get(j).getCharacters(); 
		
	}
	
	//Create test with JUnit cases 
	public void testPairwiseAlignmentStaticBanded() {
		
		//Create Aligners
				PairwiseAlignerStaticBanded aligner1 = new PairwiseAlignerStaticBanded(); 
				PairwiseAlignerSimpleGap aligner2 = new PairwiseAlignerSimpleGap();
				PairwiseAlignerDynamicKmers aligner3 = new PairwiseAlignerDynamicKmers();
				
		//Get random sequence
	

			double deltaMax = 10*0.01;
			double deltaMin = (0)*0.01;
			int cases = 50; 

			int smallError = 0; 
			for (int i = 0; i<cases; i++) {
				
				
				loadTwoSequences(); 
				//System.err.println("Testing alignments");
				//Banded Alignment
					//Set K-band value-Positive number
				int k = seq1.length()-seq2.length(); 

				if (k<0) {
					k = -k; 
					aligner1.setK(k);
				}else if (k == 0) {
					aligner1.setK(seq1.length()/4);
				}else {
					aligner1.setK(k);
					
				}
				
				String []aln1 = aligner1.calculateAlignment(seq1, seq2);
				System.out.println(aln1[0]+"\n"+aln1[1]);
				
				int maxScoreAligner1 = aligner1.getMaxScore(seq1, seq2);
				//Score static banded
	
				maxScoreAligner1 = FindScore(aln1[0],aln1[1]); 

			
				//Simple Gap Alignment
				String [] aln2 = aligner2.calculateAlignment(seq1,seq2);
				System.out.println(aln2[0]+"\n"+aln2[1]);
				
				//Score simple gap
				int maxScoreAligner2 = FindScore(aln2[0],aln2[1]); 
				
				//System.out.println(maxScoreAligner1+" "+maxScoreAligner2);
				
				double delta = Math.abs(maxScoreAligner2- maxScoreAligner1) ; 
				System.out.println(delta/Math.abs(maxScoreAligner2));
				
				assertTrue((delta/Math.abs(maxScoreAligner2))<0.1);

			}
		
		
		
		
		
		
	
		
		
		 

		


		
		
		/*
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		
		aligner3.setK(seq2.length()-seq1.length());
		String [] aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		aligner3.setK(seq1.length()-seq2.length());
		aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		
		System.err.println("Testing weird cases");
	
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		
		aligner3.setK(seq2.length()-seq1.length());
		aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		//assertNull(aln);
		
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		
		aligner3.setK(seq2.length()-seq1.length());
		aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		
		
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		
		aligner3.setK(seq2.length()-seq1.length());
		aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		
		//assertNull(aln);
		
		
		aln1 = aligner1.calculateAlignment(seq1, seq2);
		System.out.println(aln1[0]+"\n"+aln1[1]);
		
		aligner3.setK(seq1.length()-seq2.length());
		aln = aligner3.calculateAlignment(seq1, seq2);
		System.out.println(aln[0]+"\n"+aln[1]);
		*/
		
		//Old tests
		
		/*
		String seq1 = "ATGGACATCGTACACAGTATGGACATTGTACACAGT";
		String seq2 = "ATGGACAGTACATAGTATGGACAGTACATAGT";
		*/
		
		/*
		seq1 = "TACAGTTGGAATGACCACAAATCACAAATGTCAGATACCCGAAGCGCAGCG";
		seq2 =  "ACAGTTGGAATGACCACAAATCACAATGTCAGATACCCGAAGCGCAGC";
		*/
		
		/*
		seq1 = "GGATTGGGGGGGTGAAGAGTGGGGGGTGGGGGAGGGGGGGGGAGCTGGGGGGGGGAAA";
		seq2 = "GCGCAACGCGTCAGTGGGCTGATCATTAACTATCCGCTGGATGACAGGATGCCATTGCTGTGG";
		*/
		
		/*
		seq1 =  "ACTCTTTTTCGCCAAACGGATGCGCCCTC";
		seq2 = "TACTCTTTTTCGCCAAAACGGATGCGCCCTCT";
		*/
		
		/*
		seq1 = "TTTTTTTTCACTTTCTTTTTTTTTT";
		seq2 = "GAAGGCAAATCCATTCGAACCGTAATTCGTTACTGA";
		 */
		
		/*
		seq1 = "GGGAAGAAAACGATGAATGTGTG";
		seq2 = "AGAATGGGAAGAAAACGATGAATGTGTG";
		 */
		
		/*
		seq1 = "CACGGTTTATCCTGCATGCTCCGTCTTCT";
		seq2 = "GATTATCTGCATGCTCCGTCTTCT";
		 */
	}
}
