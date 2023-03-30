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
package ngsep.alignments;

import java.util.List;

import ngsep.sequences.RawRead;

public interface ReadAlignmentAlgorithm {
	/**
	 * Aligns the given read to a reference genome.
	 * It is assumed that the genome and/or a corresponding data structure is loaded in the implementation class
	 * @param read to be aligned
	 * @return List<ReadAlignment> Alignments found for the given read
	 */
	public List<ReadAlignment> alignRead (RawRead read);
}
