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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import JSci.maths.statistics.NormalDistribution;
import ngsep.math.Distribution;

/**
 * 
 * @author Jorge Duitama
 *
 */
public class LayoutBuilderKruskalPath implements LayoutBuilder {
	
	private Logger log = Logger.getLogger(LayoutBuilderKruskalPath.class.getName());

	private int minPathLength = 6;
	private boolean runImprovementAlgorithms = true;
	
	
	public Logger getLog() {
		return log;
	}
	public void setLog(Logger log) {
		this.log = log;
	}
	public int getMinPathLength() {
		return minPathLength;
	}
	public void setMinPathLength(int minPathLength) {
		this.minPathLength = minPathLength;
	}
	
	public boolean isRunImprovementAlgorithms() {
		return runImprovementAlgorithms;
	}
	public void setRunImprovementAlgorithms(boolean runImprovementAlgorithms) {
		this.runImprovementAlgorithms = runImprovementAlgorithms;
	}
	@Override
	public void findPaths(AssemblyGraph graph) {
		List<AssemblyEdge> pathEdges = graph.selectSafeEdges();
		log.info("Number of safe edges: "+pathEdges.size());
		
		List<AssemblyPath> safePaths = graph.buildPaths(pathEdges);
		//Map<Integer,Integer> vertexPathIds = calculateVertexPathIdsMap(safePaths);
		log.info("Number of paths safe edges: "+safePaths.size());
		
		addConnectingEdges(graph, safePaths, pathEdges);
		List<AssemblyPath> paths = graph.buildPaths(pathEdges);
		if(runImprovementAlgorithms) {
			Distribution [] distsEdges = calculateDistributions(pathEdges);
			log.info("Paths costs algorithm: "+paths.size());
			paths = collectAlternativeSmallPaths(graph, paths);
			log.info("Paths after collecting small embedded paths: "+paths.size());
			paths = mergeClosePaths(graph, paths, distsEdges);
			log.info("Paths after first round of merging: "+paths.size());
			//expandPathsWithEmbedded(graph, paths, distsEdges);
			//paths = mergeClosePaths(graph, paths, distsEdges);
		}
		
		for(AssemblyPath path:paths) {
			if(path.getPathLength()<minPathLength) continue;
			graph.addPath(path);
		}
		log.info("Final number of paths: "+graph.getPaths().size());
		System.out.println("Estimated N statistics");
		long [] stats = graph.estimateNStatisticsFromPaths();
		if(stats!=null) NStatisticsCalculator.printNStatistics(stats, System.out);
	}
 
	private AssemblyVertex [] extractEndVertices (List<AssemblyPath> paths) {
		int p = paths.size();
		AssemblyVertex [] vertices = new AssemblyVertex[2*p];
		int v=0;
		for(int i=0;i<p;i++) {
			AssemblyPath path = paths.get(i);
			vertices[v] = path.getVertexLeft();
			v++;
			vertices[v] = path.getVertexRight();
			v++;
		}
		return vertices;
	}
	private void addConnectingEdges(AssemblyGraph graph, List<AssemblyPath> paths, List<AssemblyEdge> pathEdges) {
		NormalDistribution distIKBP = graph.estimateDistributions(pathEdges, new HashSet<Integer>())[5];
		AssemblyVertex [] vertices = extractEndVertices(paths);
		log.info("KruskalPathAlgorithm. Extracted "+vertices.length+" end vertices");
		List<AssemblyEdge> candidateEdges = new ArrayList<AssemblyEdge>();
		for(int i=0;i<vertices.length;i++) {
			List<AssemblyEdge> edgesVI = graph.getEdges(vertices[i]);
			for(AssemblyEdge edge:edgesVI) {
				if(edge.isSameSequenceEdge()) continue;
				if(graph.isEmbedded(edge.getVertex1().getSequenceIndex())) continue;
				if(graph.isEmbedded(edge.getVertex2().getSequenceIndex())) continue;
				//Add each candidate vertex only one time
				candidateEdges.add(edge);
			}
		}
		log.info("KruskalPathAlgorithm. selected "+candidateEdges.size()+" candidate edges");
		Collections.sort(candidateEdges,(e1,e2)->e1.getCost()-e2.getCost());
		//Collections.sort(candidateEdges,(e1,e2)->e2.getScore()-e1.getScore());
		log.info("KruskalPathAlgorithm. Sorted "+candidateEdges.size()+" edges");
		List<AssemblyEdge> selectedEdges = selectEdgesToMergePaths(candidateEdges,vertices, distIKBP);
		log.info("KruskalPathAlgorithm. Selected "+selectedEdges.size()+" edges for paths");
		pathEdges.addAll(selectedEdges);
	}
	private List<AssemblyEdge> selectEdgesToMergePaths(List<AssemblyEdge> candidateEdges, AssemblyVertex [] vertices, NormalDistribution distIKBP) {
		double limitIKBP = distIKBP.getMean()+15*Math.sqrt(distIKBP.getVariance());
		log.info("Limit for IKBP: "+limitIKBP+" Average: "+distIKBP.getMean()+" variance: "+distIKBP.getVariance());
		int n = vertices.length;
		Map<Integer,Integer> verticesPos = new HashMap<Integer, Integer>();
		for(int i=0;i<n;i++) {
			verticesPos.put(vertices[i].getUniqueNumber(), i);
		}
		int [] clusters = new int[n];
		boolean [] used = new boolean[n];
		Arrays.fill(used, false);
		for(int i=0;i<n;i++) {
			clusters[i] = i/2;
		}
		List<AssemblyEdge> answer = new ArrayList<AssemblyEdge>();
		for(AssemblyEdge nextEdge:candidateEdges) {
			//if(nextEdge.getEdgeAssemblyGraph().getVertex1().getUniqueNumber()==2854) System.out.println("score: "+calculateCost(nextEdge, edgesStats)+" edge: "+nextEdge.getEdgeAssemblyGraph());
			//if(answer.size()<20) System.out.println("score: "+calculateCost(nextEdge, edgesStats)+ " edge: "+nextEdge.getEdgeAssemblyGraph());
			AssemblyVertex v1 = nextEdge.getVertex1();
			AssemblyVertex v2 = nextEdge.getVertex2();
			if(v1==null || v2 == null) continue;
			Integer posV1 = verticesPos.get(v1.getUniqueNumber());
			Integer posV2 = verticesPos.get(v2.getUniqueNumber());
			if(posV1==null || posV2==null) continue;
			//if(v1.getUniqueNumber()==-97856 || v2.getUniqueNumber()==69473) System.out.println("SelectingEdges. next edge: "+nextEdge+" used: "+used[posV1]+" "+used[posV2]+" clusters: "+clusters[posV1]+" "+clusters[posV2]);
			
			if(used[posV1] || used[posV2]) continue;
			if(nextEdge.getIndelsPerKbp()>limitIKBP) continue;
			int c1 = clusters[posV1];
			int c2 = clusters[posV2];
			
			if(c1!=c2) {
				answer.add(nextEdge);
				used[posV1] =used[posV2] = true;
				for(int i=0;i<n;i++) {
					if(clusters[i]==c2) clusters[i] = c1;
				}
			}
		}
		return answer;
	}
	private Distribution[] calculateDistributions(List<AssemblyEdge> pathEdges) {
		Distribution costsDistribution = new Distribution(0, 100000, 1000);
		Distribution ikbpDistribution = new Distribution(0, 50, 0.25);
		for(AssemblyEdge edge:pathEdges) {
			if(edge.isSameSequenceEdge()) continue;
			costsDistribution.processDatapoint(edge.getCost());
			ikbpDistribution.processDatapoint(edge.getIndelsPerKbp());
		}
		//log.info("Average cost path edges: "+costsDistribution.getAverage()+" STDEV: "+Math.sqrt(costsDistribution.getVariance()));
		//costsDistribution.printDistribution(System.out);
		Distribution[] answer = {costsDistribution,ikbpDistribution}; 
		return answer;
	}
	private List<AssemblyPath> collectAlternativeSmallPaths(AssemblyGraph graph, List<AssemblyPath> paths) {
		Map<Integer,VertexPathLocation> vertexPositions = getPathPositionsMap(paths);
		Set<Integer> indexesToRemove = new HashSet<>();
		for(int i=0;i<paths.size();i++) {
			AssemblyPath path = paths.get(i);
			if(path.getPathLength()>10) continue;
			AssemblyVertex leftVertex = path.getVertexLeft();
			AssemblyVertex rightVertex = path.getVertexRight();
			AssemblyEdge leftEdge = graph.getEdgeMinCost(leftVertex);
			AssemblyEdge rightEdge = graph.getEdgeMinCost(rightVertex);
			if(leftEdge==null || rightEdge == null) continue;
			AssemblyVertex leftConnecting = leftEdge.getConnectingVertex(leftVertex);
			AssemblyVertex rightConnecting = rightEdge.getConnectingVertex(rightVertex);
			if(leftConnecting==null || rightConnecting == null) continue;
			VertexPathLocation leftLocation = vertexPositions.get(leftConnecting.getUniqueNumber());
			VertexPathLocation rightLocation = vertexPositions.get(rightConnecting.getUniqueNumber());
			if(leftLocation==null || rightLocation == null) continue;
			if(leftLocation.getPath()==path) continue;
			if(leftLocation.getPath()!=rightLocation.getPath()) continue;
			AssemblyPath hostPath = leftLocation.getPath();
			
			//log.info("Possible integration of path id: "+pathId+" into "+connectingPathId+" lengths: "+path1.getPathLength()+" "+path2.getPathLength()+" conecting pos: "+connectionStart.getPathPosition()+" "+connectionEnd.getPathPosition());
			if(0.1*hostPath.getPathLength()<path.getPathLength()) continue;
			if(Math.abs(leftLocation.getPathPosition()-rightLocation.getPathPosition())>1.5*path.getPathLength()) continue;
			hostPath.addAlternativeSmallPath(path);
			indexesToRemove.add(i);
		}
		log.info("Internal path ids: "+indexesToRemove.size());
		List<AssemblyPath> answer = new ArrayList<AssemblyPath>();
		for(int i=0;i<paths.size();i++) {
			if(!indexesToRemove.contains(i)) answer.add(paths.get(i));
		}
		return answer;
	}
	private Map<Integer,VertexPathLocation> getPathPositionsMap (List<AssemblyPath> paths) {
		Map<Integer,VertexPathLocation> vertexPositions = new HashMap<Integer,VertexPathLocation>();
		for(int i=0;i<paths.size();i++) {
			AssemblyPath path = paths.get(i);
			List<AssemblyEdge> edges = path.getEdges();
			int n = edges.size();
			AssemblyVertex vertexLeft = path.getVertexLeft();
			vertexPositions.put(vertexLeft.getUniqueNumber(), new VertexPathLocation(vertexLeft.getUniqueNumber(), path, n, 0));
			AssemblyVertex lastVertex = vertexLeft;
			int k = 1;
			for(AssemblyEdge edge:edges) {
				AssemblyVertex nextVertex = edge.getConnectingVertex(lastVertex);
				vertexPositions.put(nextVertex.getUniqueNumber(), new VertexPathLocation(nextVertex.getUniqueNumber(), path, n, k));
				k++;
				lastVertex = nextVertex;
			}
		}
		return vertexPositions;
	}
	private boolean expandPathsWithEmbedded(AssemblyGraph graph, List<AssemblyPath> paths, Distribution [] dists) {
		double averageCost = dists[0].getAverage();
		double averageIKBP = dists[1].getAverage();
		log.info("Expanding paths with embedded. Average cost: "+averageCost+" average IKBP: "+averageIKBP);
		Set<Integer> usedEmbedded = new HashSet<Integer>();
		for(AssemblyPath path:paths) {
			
			//System.out.println("Expanding paths with embedded. Next start: "+endVertex);
			while(true) {
				AssemblyVertex endVertex = path.getVertexLeft();
				AssemblyEdge edge = graph.getEdgeMinCost(endVertex);
				if(edge==null) break;
				if(edge.getCost()>2*averageCost || edge.getIndelsPerKbp()>averageIKBP) break;
				AssemblyVertex next = edge.getConnectingVertex(endVertex);
				if(!usedEmbedded.contains(next.getSequenceIndex()) && graph.isEmbedded(next.getSequenceIndex())) {
					log.info("Expanding path starting with: "+path.getVertexLeft()+" with embedded. Edge: "+edge);
					path.connectEdgeLeft(graph, edge);
					endVertex = path.getVertexLeft();
					usedEmbedded.add(endVertex.getSequenceIndex());
				} else break;
			}
			//System.out.println("Expanding paths with embedded. Next end: "+endVertex);
			while(true) {
				AssemblyVertex endVertex = path.getVertexRight();
				AssemblyEdge edge = graph.getEdgeMinCost(endVertex);
				//if(endVertex.getUniqueNumber()==24728) System.out.println("Expanding paths with embedded. Best overlap: "+edge);
				if(edge==null) break;
				if(edge.getCost()>2*averageCost || edge.getIndelsPerKbp()>averageIKBP) break;
				AssemblyVertex next = edge.getConnectingVertex(endVertex);
				if(!usedEmbedded.contains(next.getSequenceIndex()) && graph.isEmbedded(next.getSequenceIndex())) {
					log.info("Expanding path ending with: "+path.getVertexRight()+" with embedded. Edge: "+edge);
					path.connectEdgeRight(graph, edge);
					endVertex = path.getVertexRight();
					usedEmbedded.add(endVertex.getSequenceIndex());
				} else break;
			}
		}
		return usedEmbedded.size()>0;
	}
	private List<AssemblyPath> mergeClosePaths (AssemblyGraph graph, List<AssemblyPath> paths, Distribution [] dists) {
		Map<Integer,VertexPathLocation> vertexPositions = getPathPositionsMap(paths);
		Map<Integer,Integer> pathVertexStarts = new HashMap<Integer, Integer>();
		Map<Integer,Integer> pathVertexEnds = new HashMap<Integer, Integer>();
		//Find edges connecting paths
		Map<String,PathEndJunctionEdge> pathEndEdges = new HashMap<String, PathEndJunctionEdge>();

		Distribution costsDistribution = dists[0];
		for(int i=0;i<paths.size();i++) {
			AssemblyPath path = paths.get(i);
			int pathId = i+1;
			path.setPathId(pathId);
			pathVertexStarts.put(pathId,path.getVertexLeft().getUniqueNumber());
			pathVertexEnds.put(pathId,path.getVertexRight().getUniqueNumber());
		}
		//Build edges
		Set<Integer> allVertexEnds = new HashSet<Integer>();
		allVertexEnds.addAll(pathVertexStarts.values());
		allVertexEnds.addAll(pathVertexEnds.values());
		for(int vertexId:allVertexEnds) {
			AssemblyVertex v1 = graph.getVertexByUniqueId(vertexId);
			AssemblyEdge edge = graph.getEdgeBestOverlap(v1);
			//if(vertexId==-32163) System.out.println("FInding edges to merge paths. Edge: "+edge);
			addVote(v1, edge, costsDistribution, vertexPositions, pathEndEdges);
			
			AssemblyEdge edgeC = graph.getEdgeMinCost(v1);
			if(edgeC!=edge) addVote(v1, edgeC, costsDistribution, vertexPositions, pathEndEdges);
		}
		log.info("Found "+pathEndEdges.size()+" candidate edges connecting vertices");
		
		
		Map<Integer,List<Integer>> mergedPathIds = findPathsToMerge(pathEndEdges.values(),paths.size());
		//log.info("Merged path ids: "+mergedPathIds.values());
		List<AssemblyPath> answer = new ArrayList<AssemblyPath>(mergedPathIds.size());
		
		for(List<Integer> idsPath:mergedPathIds.values()) {
			AssemblyPath nextPath = null;
			int lastEndId = 0;
			for(int pathEndId:idsPath) {
				if(pathEndId!=-lastEndId) {
					lastEndId = pathEndId;
					continue;
				}
				if(pathEndId > 0) {
					AssemblyPath nextInputPath = paths.get(pathEndId-1);
					if(nextPath==null) {
						nextInputPath.reverse();
						nextPath = nextInputPath;
					} else if(!nextPath.connectPathRight(graph, nextInputPath, true)) {
						answer.add(nextPath);
						nextPath = nextInputPath;
					}
				} else {
					AssemblyPath nextInputPath = paths.get(lastEndId-1);
					if(nextPath==null) {
						nextPath = nextInputPath;
					} else if(!nextPath.connectPathRight(graph, nextInputPath, false)) {
						answer.add(nextPath);
						nextPath = nextInputPath;
					}
				}
				lastEndId = pathEndId;
			}
			//log.info("Next final path size: "+nextPath.size());
			if(nextPath!=null) answer.add(nextPath);
		}
		
		return answer;
	}
	
	private void addVote (AssemblyVertex vertexEnd, AssemblyEdge edge, Distribution costsDistribution, Map<Integer,VertexPathLocation> vertexPositions, Map<String,PathEndJunctionEdge> pathEndEdges) {
		if(edge == null) return;
		if(edge.getCost()>2*costsDistribution.getAverage()) return;
		VertexPathLocation v1Loc = vertexPositions.get(vertexEnd.getUniqueNumber());
		if(v1Loc == null) return;
		AssemblyVertex v2 = edge.getConnectingVertex(vertexEnd);
		VertexPathLocation v2Loc = vertexPositions.get(v2.getUniqueNumber());
		if(v2Loc == null) return;
		if (v1Loc.getPath() == v2Loc.getPath()) return;
		//vertexEndsBestOvEdgeConnectingVertex.put(vertexId, v2Loc);
		int pathEndV1 = v1Loc.getPath().getPathId();
		if(v1Loc.getPathPosition()>v1Loc.getPathLength()-4) pathEndV1 = -pathEndV1;
		else if (v1Loc.getPathPosition()>4) return;
		int pathEndV2 = v2Loc.getPath().getPathId();
		if(v2Loc.getPathPosition()>v2Loc.getPathLength()-4) pathEndV2 = -pathEndV2;
		else if (v2Loc.getPathPosition()>4) return;
		int minId = Math.min(pathEndV1, pathEndV2);
		int maxId = Math.max(pathEndV1, pathEndV2);
		String key = "F"+minId+"L"+maxId;
		PathEndJunctionEdge pathEndEdge = pathEndEdges.computeIfAbsent(key, v->new PathEndJunctionEdge(minId, maxId));
		log.info("Adding vote to relationship between path ends "+minId+" "+maxId+" edge: "+edge);
		pathEndEdge.addVote(edge.getCost());
	}
	
	
	private Map<Integer,List<Integer>> findPathsToMerge(Collection<PathEndJunctionEdge> pathEndEdges, int n) {
		List<PathEndJunctionEdge> pathEndEdgesList = new ArrayList<PathEndJunctionEdge>(pathEndEdges);
		Collections.sort(pathEndEdgesList,(e1,e2)->e1.getCost()-e2.getCost());
		Map<Integer,Integer> pathGroups = new HashMap<Integer, Integer>();
		
		Map<Integer,List<Integer>> answer = new TreeMap<Integer,List<Integer>>();
		Set<Integer> usedPathEnds = new HashSet<Integer>();
		for(int i=0;i<n;i++) {
			int pathId = i+1;
			pathGroups.put(pathId, pathId);
			pathGroups.put(-pathId, pathId);
			LinkedList<Integer> nextPath = new LinkedList<Integer>();
			nextPath.add(pathId);
			nextPath.add(-pathId);
			answer.put(pathId,nextPath);
		}
		
		for(PathEndJunctionEdge pathEdge:pathEndEdgesList) {
			int p1 = pathEdge.getPath1EndId();
			int p2 = pathEdge.getPath2EndId();
			if(usedPathEnds.contains(p1) || usedPathEnds.contains(p2)) continue;
			int g1 = pathGroups.get(p1);
			int g2 = pathGroups.get(p2);
			if(g1==g2) continue;
			//Merge path1 with path2
			
			List<Integer> path1 = answer.get(g1);
			List<Integer> path2 = answer.get(g2);
			if(path2.get(path2.size()-1)==p2) {
				Collections.reverse(path2);
			} else if (path2.get(0)!=p2) {
				continue;
			}
			boolean lastP1 = false;
			if(path1.get(path1.size()-1)==p1) {
				lastP1 = true;
			} else if (path1.get(0)!=p1) {
				continue;
			}
			for(int i:path2) {
				if(lastP1) path1.add(i);
				else path1.add(0, i);
				pathGroups.put(i, g1);
			}
			answer.remove(g2);
			usedPathEnds.add(p1);
			usedPathEnds.add(p2);
		}
		return answer;
	}

}
class VertexPathLocation {
	private int vertexId;
	private AssemblyPath path;
	private int pathLength;
	private int pathPosition;
	public VertexPathLocation(int vertexId, AssemblyPath path, int pathLength, int pathPosition) {
		super();
		this.vertexId = vertexId;
		this.path = path;
		this.pathLength = pathLength;
		this.pathPosition = pathPosition;
	}
	public int getVertexId() {
		return vertexId;
	}
	public AssemblyPath getPath() {
		return path;
	}
	public int getPathLength() {
		return pathLength;
	}
	public int getPathPosition() {
		return pathPosition;
	}
	
}
class PathEndJunctionEdge {
	private int path1EndId;
	private int path2EndId;
	private int totalCost=0;
	private int countVotes=0;
	
	
	
	public PathEndJunctionEdge(int path1EndId, int path2EndId) {
		super();
		this.path1EndId = path1EndId;
		this.path2EndId = path2EndId;
	}
	public void addVote (int cost) {
		totalCost+=cost;
		countVotes++;
	}
	public int getPath1EndId() {
		return path1EndId;
	}
	public int getPath2EndId() {
		return path2EndId;
	}
	public int getCost() {
		if(countVotes==0) return Integer.MAX_VALUE;
		return totalCost/countVotes;
	}
	public String toString() {
		return "P1: "+path1EndId+" P2: "+path2EndId+" Total cost: "+totalCost+" Votes: "+countVotes;
	}
	
	
}
