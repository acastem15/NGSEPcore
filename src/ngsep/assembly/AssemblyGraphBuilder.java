package ngsep.assembly;

import java.util.List;
import java.util.Map;

public interface AssemblyGraphBuilder {
	/**
	 * Builds an assembly graph from the given sequences
	 * @param sequences to process
	 */
	public AssemblyGraph buildAssemblyGraph (List<CharSequence> sequences);
}
