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

import java.util.Map;

import JSci.maths.statistics.ChiSqrDistribution;
import JSci.maths.statistics.NormalDistribution;
import ngsep.math.LogMath;

/**
 * 
 * @author Jorge Duitama
 *
 */
public class AssemblySequencesRelationshipScoresCalculator {
	private int debugIdx = -1;
	private Map<Integer,Double> averageIKBPVertices;
	private Map<Integer,Double> averageIKBPEmbedded;
	private double weightsSecondaryFeatures = 0.5;
	
	public Map<Integer, Double> getAverageIKBPVertices() {
		return averageIKBPVertices;
	}
	public void setAverageIKBPVertices(Map<Integer, Double> averageIKBPVertices) {
		this.averageIKBPVertices = averageIKBPVertices;
	}
	public Map<Integer, Double> getAverageIKBPEmbedded() {
		return averageIKBPEmbedded;
	}
	public void setAverageIKBPEmbedded(Map<Integer, Double> averageIKBPEmbedded) {
		this.averageIKBPEmbedded = averageIKBPEmbedded;
	}
	
	public double getWeightsSecondaryFeatures() {
		return weightsSecondaryFeatures;
	}
	public void setWeightsSecondaryFeatures(double weightsSecondaryFeatures) {
		this.weightsSecondaryFeatures = weightsSecondaryFeatures;
	}
	public int calculateScore(AssemblySequencesRelationship relationship, NormalDistribution[] edgesDists) {
		//NormalDistribution overlapD = edgesDists[0];
		double evProp = relationship.getEvidenceProportion();
		//double overlapRel = (double)relationship.getOverlap()/overlapD.getMean();
		//if(overlapRel>1) overlapRel = 1;
		//double maxIKBP = getMaxAverageIKBP (relationship);
		//return edge.getCoverageSharedKmers();
		//return edge.getRawKmerHits();
		//double score = relationship.getWeightedCoverageSharedKmers();
		//double score = relationship.getWeightedCoverageSharedKmers()*evProp;
		//double score = (relationship.getOverlap()*w1+relationship.getWeightedCoverageSharedKmers()*w2)*evProp;
		//double score = (0.001*relationship.getOverlap())*relationship.getWeightedCoverageSharedKmers();
		//double score = overlapRel*relationship.getWeightedCoverageSharedKmers();
		//double score = overlapRel*relationship.getWeightedCoverageSharedKmers()*evProp/Math.sqrt(1+relationship.getIndelsPerKbp());
		double score = Math.sqrt(relationship.getOverlap())*(0.01*relationship.getWeightedCoverageSharedKmers())*Math.sqrt(evProp);
		//double score = Math.sqrt(relationship.getOverlap())*(0.01*relationship.getWeightedCoverageSharedKmers())*Math.sqrt(evProp);
		if(weightsSecondaryFeatures>0) {
			score*=Math.sqrt(evProp);
			score/=Math.sqrt(Math.max(1, relationship.getIndelsPerKbp()));
		}
		//double score = Math.sqrt(relationship.getOverlap())*(0.01*relationship.getWeightedCoverageSharedKmers())*Math.sqrt(evProp)/Math.sqrt(1+relationship.getIndelsPerKbp());
		//double score = (0.001*relationship.getOverlap())*relationship.getWeightedCoverageSharedKmers()*evProp;
		//if(logRelationship(relationship)) System.out.println("Relationship: "+relationship+" Evidence proportion: "+evProp+" score: "+score);
		//double score = relationship.getOverlap()*evProp+relationship.getWeightedCoverageSharedKmers()*Math.sqrt(overlapProportion);
		//double score = (relationship.getOverlap()+relationship.getWeightedCoverageSharedKmers())*evProp*evProp;
		
		/*NormalDistribution globalIKBP = edgesDists[5];
		double globalMean = globalIKBP.getMean();
		double avg = Math.max(globalMean, maxIKBP);
		//Average is not controlled in the chimera detection for embedded relationships
		//if(relationship instanceof AssemblyEmbedded) avg = Math.min(globalMean*2, avg);
		NormalDistribution normalDistIkbp = new NormalDistribution(avg,Math.max(avg,globalIKBP.getVariance()));
		double pValueIKBP = 1-normalDistIkbp.cumulative(relationship.getIndelsPerKbp());
		if(pValueIKBP>0.0001) pValueIKBP = 0.5;
		//pValueIKBP*=2;
		score*=pValueIKBP;*/
		
		return (int)Math.round(score);
	}
	private double getMaxAverageIKBP(AssemblySequencesRelationship relationship) {
		Double avgIKBP1;
		Double avgIKBP2;
		if(relationship instanceof AssemblyEmbedded) {
			AssemblyEmbedded embedded = (AssemblyEmbedded)relationship;
			avgIKBP1 = averageIKBPEmbedded.get(embedded.getHostId());
			avgIKBP2 = averageIKBPEmbedded.get(embedded.getSequenceId());
		} else {
			AssemblyEdge edge = (AssemblyEdge) relationship;
			avgIKBP1 = averageIKBPVertices.get(edge.getVertex1().getUniqueNumber());
			avgIKBP2 = averageIKBPVertices.get(edge.getVertex2().getUniqueNumber());
		}
		if(avgIKBP1==null) avgIKBP1=1.0;
		if(avgIKBP2==null) avgIKBP2=1.0;
		return Math.max(avgIKBP1, avgIKBP2);
	}
	public int calculateCost(AssemblySequencesRelationship relationship, NormalDistribution[] edgesDists) {
		NormalDistribution overlapD = edgesDists[0];
		NormalDistribution wcskD = edgesDists[1];
		NormalDistribution wcskPropD = edgesDists[2];
		NormalDistribution overlapSD = edgesDists[3];
		NormalDistribution evPropD = edgesDists[4];
		NormalDistribution indelsKbpD = edgesDists[5];
		//double maxIKBP = getMaxAverageIKBP(relationship);
		//double avg = Math.max(indelsKbpD.getMean(), maxIKBP);
		//NormalDistribution normalDistIkbp = new NormalDistribution(avg,Math.max(avg,indelsKbpD.getVariance()));
		ChiSqrDistribution normalDistIkbp = new ChiSqrDistribution(2);
		int maxIndividualCost = 10;
		double w = weightsSecondaryFeatures;
		double [] individualCosts = new double[6];
		double [] limitPValues = {1,0.5,0.1,0.05,0.5,0.25};
		double [] weights      = {1,  1,0,   0,w,w};
		
		double cumulativeOverlap = overlapD.cumulative(relationship.getOverlap());
		//if(pValueOTP>0.5) pValueOTP = 1- pValueOTP;
		individualCosts[0] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[0],cumulativeOverlap),maxIndividualCost);
		//double cost1 = maxIndividualCost*(1-cumulativeOverlap);
		
		double cumulativeWCSK = wcskD.cumulative(relationship.getWeightedCoverageSharedKmers());
		individualCosts[1] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[1],cumulativeWCSK),maxIndividualCost);
		
		double cumulativeWCSKProp = wcskPropD.cumulative((double)relationship.getWeightedCoverageSharedKmers()/(relationship.getOverlap()+1));
		//individualCosts[2] = maxIndividualCost*(1-cumulativeWCSK);
		
		individualCosts[2] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[2], cumulativeWCSKProp),maxIndividualCost);
		
		//TODO: Save overlapSD for embedded 
		double pValueOverlapSD = 0.5;
		if(relationship instanceof AssemblyEdge) pValueOverlapSD = 1-overlapSD.cumulative(((AssemblyEdge)relationship).getOverlapStandardDeviation());
		individualCosts[3] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[3], pValueOverlapSD),maxIndividualCost);
		
		double cumulativeEvProp = evPropD.cumulative(relationship.getEvidenceProportion());
		individualCosts[4] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[4], cumulativeEvProp),maxIndividualCost);
		//individualCosts[4] = 100.0*(1.0-relationship.getEvidenceProportion());
		
		double pValueIKBP = 1-normalDistIkbp.cumulative(relationship.getIndelsPerKbp());
		individualCosts[5] = LogMath.negativeLog10WithLimit(Math.min(limitPValues[5],pValueIKBP),maxIndividualCost);
		//individualCosts[5] = Math.max(indelsKbpD.getMean(), relationship.getIndelsPerKbp());
		
		
		double costD = 0;
		for(int i=0;i<weights.length;i++) {
			costD+=individualCosts[i]*weights[i];
		}

		int cost = (int)(10000.0*costD);
		cost += 10*cumulativeOverlap;
		//cost+= (int) (100.0*(1-cumulativeOverlap));
		//cost+= (int) (1000*(1-pValueOTP)*(1-pValueWCTP));

		if( logRelationship(relationship)) System.out.println("CalculateCost. Rel: "+relationship+" Values "+cumulativeOverlap+" "+cumulativeWCSK+" "+cumulativeWCSKProp+" "+cumulativeEvProp+" "+pValueIKBP+" costs: "+individualCosts[0]+" "+individualCosts[1]+" "+individualCosts[2]+" "+individualCosts[4]+" "+individualCosts[5]+" cost: " +cost+" IKBP rel: "+relationship.getIndelsPerKbp());
		
		return cost;
	}
	private boolean logRelationship(AssemblySequencesRelationship relationship) {
		if (relationship instanceof AssemblyEdge) {
			AssemblyEdge edge = (AssemblyEdge)relationship;
			return edge.getVertex1().getSequenceIndex()==debugIdx || edge.getVertex2().getSequenceIndex()==debugIdx;
		} else if (relationship instanceof AssemblyEmbedded) {
			AssemblyEmbedded embedded = (AssemblyEmbedded)relationship;
			return embedded.getSequenceId() == debugIdx;
		}
		return false;
	}
}
